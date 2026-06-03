package com.uxplima.uxmessentials.communication.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.communication.adapter.inbound.command.CommunicationCommands;
import com.uxplima.uxmessentials.communication.adapter.inbound.listener.ChatLockListener;
import com.uxplima.uxmessentials.communication.adapter.inbound.listener.ConnectionMessageListener;
import com.uxplima.uxmessentials.communication.adapter.inbound.listener.DeathMessageListener;
import com.uxplima.uxmessentials.communication.adapter.outbound.AnnouncerTask;
import com.uxplima.uxmessentials.communication.adapter.outbound.AtomicSequenceCounter;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitInfoSender;
import com.uxplima.uxmessentials.communication.adapter.outbound.PdcBroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.adapter.outbound.ThreadLocalRandomSource;
import com.uxplima.uxmessentials.communication.application.BroadcastOptOut;
import com.uxplima.uxmessentials.communication.application.CommunicationNotifier;
import com.uxplima.uxmessentials.communication.application.InfoRegistry;
import com.uxplima.uxmessentials.communication.application.NextAnnouncement;
import com.uxplima.uxmessentials.communication.application.ResolveConnectionMessage;
import com.uxplima.uxmessentials.communication.application.ResolveDeathMessage;
import com.uxplima.uxmessentials.communication.application.ResolveJoinMessage;
import com.uxplima.uxmessentials.communication.application.ResolveQuitMessage;
import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.application.port.RandomSource;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the communication context's adapters and use cases over the injected kernel ports and the
 * {@code communication.conf} content, and produces everything the plugin must register: the Brigadier command
 * list (the static {@code /broadcasttoggle} plus the config-derived info-page commands), the join/quit/death
 * connection listeners, and the self-rescheduling announcer timer on the {@code Scheduler} port. This is the one
 * place the communication context is wired — nothing else news up its classes.
 *
 * <p>The context persists nothing: the per-player opt-out bit is PDC-backed (survives relog), the sequence
 * counters are transient, and the announcer schedule and info pages are config-authored. The operator content is
 * read once into {@link CommunicationSettings} and rendered through MiniMessage; the plugin's own
 * {@code /broadcasttoggle} confirmation and missing-page error are {@code MessageKey}s through the
 * {@link CommunicationNotifier}, keeping the parity-checked keys and the unchecked operator content apart.
 */
@NullMarked
public final class CommunicationWiring {

    private static final String CONTENT_FILE = "modules/communication/config.conf";

    private CommunicationWiring() {}

    /** Build the communication adapters and use cases from {@code plugin} and {@code ctx}, ready to register. */
    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        Path file = plugin.getDataFolder().toPath().resolve(CONTENT_FILE);
        CommunicationSettings settings = new CommunicationSettings(file, kernel.log());
        AtomicBoolean running = new AtomicBoolean(true);

        BroadcastOptOutStore optOutStore = new PdcBroadcastOptOutStore(plugin);
        RandomSource random = new ThreadLocalRandomSource();
        BukkitInfoSender infoSender = new BukkitInfoSender(kernel.messageSink());
        CommunicationNotifier notifier = new CommunicationNotifier(kernel.messages(), kernel.messageSink());
        InfoRegistry registry = settings.infoRegistry();
        ChatLock chatLock = new ChatLock();

        CommunicationServices services = assemble(kernel, settings, optOutStore, random, notifier);
        BukkitAnnouncerBroadcaster broadcaster = new BukkitAnnouncerBroadcaster(kernel.messageSink(), optOutStore);
        AnnouncerTask announcer = new AnnouncerTask(
                kernel.scheduler(),
                services.nextAnnouncement(),
                broadcaster,
                settings::announcerSchedule,
                running::get);
        List<CommandRegistration> commands = CommunicationCommands.all(
                services.broadcastOptOut(),
                registry,
                infoSender,
                notifier,
                kernel.messages(),
                broadcaster,
                kernel.messageSink(),
                chatLock);
        List<Listener> listeners = listeners(services, registry, infoSender, settings, chatLock, notifier);
        return new Wired(commands, listeners, announcer, running);
    }

    private static CommunicationServices assemble(
            KernelPorts kernel,
            CommunicationSettings settings,
            BroadcastOptOutStore optOutStore,
            RandomSource random,
            CommunicationNotifier notifier) {
        ResolveConnectionMessage engine = new ResolveConnectionMessage(new AtomicSequenceCounter(), random);
        return new CommunicationServices(
                new ResolveJoinMessage(engine, settings::joinPolicy),
                new ResolveQuitMessage(engine, settings::quitPolicy),
                new ResolveDeathMessage(engine, settings::deathPolicy),
                new NextAnnouncement(settings::announcerSchedule, random),
                new BroadcastOptOut(optOutStore, notifier, kernel.events(), Clock.systemUTC()));
    }

    private static List<Listener> listeners(
            CommunicationServices services,
            InfoRegistry registry,
            BukkitInfoSender infoSender,
            CommunicationSettings settings,
            ChatLock chatLock,
            CommunicationNotifier notifier) {
        return List.of(
                new ConnectionMessageListener(services.resolveJoin(), services.resolveQuit(), settings),
                new DeathMessageListener(services.resolveDeath(), registry, infoSender, settings),
                new ChatLockListener(chatLock, notifier));
    }

    /**
     * Everything the communication module contributes once wired: the Brigadier commands (static
     * {@code /broadcasttoggle} plus the config-derived info pages), the connection/death listeners, the
     * self-rescheduling announcer timer, and the {@code running} flag the timer observes.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join/quit/death listeners to register
     * @param announcer the self-rescheduling announcer timer, armed by the caller
     * @param running the flag flipped false on stop so the announcer exits
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            AnnouncerTask announcer,
            AtomicBoolean running) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(announcer, "announcer");
            Objects.requireNonNull(running, "running");
        }

        /** Arm the announcer timer. */
        public void startBackgroundWork() {
            announcer.start();
        }

        /** Stop the announcer timer. */
        public void stop() {
            running.set(false);
        }
    }
}
