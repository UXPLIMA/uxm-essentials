package com.uxplima.uxmessentials.communication.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.communication.adapter.inbound.command.CommunicationCommands;
import com.uxplima.uxmessentials.communication.adapter.inbound.listener.AdvancementMessageListener;
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
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelBroadcaster;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the communication context's adapters and use cases over the injected kernel ports and the operator
 * content under {@code modules/communication/}, and produces everything the plugin must register: the Brigadier command
 * list (the static {@code /broadcast}, {@code /broadcasttoggle}, and {@code /announce} plus the config-derived
 * info-page commands), the join/quit/death connection listeners, the advancement-notification listener, and the
 * self-rescheduling announcer timer on the {@code Scheduler} port. The announcer and the advancement listener fan
 * out through the shared {@link ChannelBroadcaster} so their multi-channel delivery, PlaceholderAPI expansion, and
 * per-viewer opt-out gating match vote's broadcaster. The advancement listener is registered unconditionally and
 * gated by its live config, so {@code /uxmess reload communication} can enable it without re-registration. This is
 * the one place the communication context is wired — nothing else news up its classes.
 *
 * <p>The context persists nothing: the per-player opt-out bit is PDC-backed (survives relog), the sequence
 * counters are transient, and the announcer schedule and info pages are config-authored. The operator content is
 * read once into {@link CommunicationSettings} and rendered through MiniMessage; the plugin's own
 * {@code /broadcasttoggle} confirmation and missing-page error are {@code MessageKey}s through the
 * {@link CommunicationNotifier}, keeping the parity-checked keys and the unchecked operator content apart.
 */
@NullMarked
public final class CommunicationWiring {

    private static final String MODULE_DIR = "modules/communication";

    private CommunicationWiring() {}

    /** Build the communication adapters and use cases from {@code plugin} and {@code ctx}, ready to register. */
    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        Path dir = plugin.getDataFolder().toPath().resolve(MODULE_DIR);
        CommunicationSettings settings = new CommunicationSettings(dir, kernel.log());
        AtomicBoolean running = new AtomicBoolean(true);

        BroadcastOptOutStore optOutStore = new PdcBroadcastOptOutStore(plugin);
        RandomSource random = new ThreadLocalRandomSource();
        BukkitInfoSender infoSender = new BukkitInfoSender(kernel.messageSink());
        CommunicationNotifier notifier = new CommunicationNotifier(kernel.messages(), kernel.messageSink());
        InfoRegistry registry = settings.infoRegistry();
        ChatLock chatLock = new ChatLock();

        CommunicationServices services = assemble(kernel, settings, optOutStore, random, notifier);
        ChannelBroadcaster channelBroadcaster = new ChannelBroadcaster(kernel.scheduler(), settings.announcerDisplay());
        BukkitAnnouncerBroadcaster broadcaster = new BukkitAnnouncerBroadcaster(
                kernel.messageSink(), optOutStore, channelBroadcaster, CommunicationWiring::conditionContext);
        AnnouncerTask announcer = new AnnouncerTask(
                kernel.scheduler(), services.nextAnnouncement(), broadcaster, settings::announcerConfig, running::get);
        List<CommandRegistration> commands = CommunicationCommands.all(
                services.broadcastOptOut(),
                registry,
                infoSender,
                notifier,
                kernel.messages(),
                broadcaster,
                announcer,
                kernel.scheduler(),
                kernel.messageSink(),
                chatLock,
                settings);
        List<Listener> listeners = listeners(
                services, registry, infoSender, settings, chatLock, notifier, channelBroadcaster, optOutStore);
        return new Wired(commands, listeners, announcer, running);
    }

    /**
     * Gather everything an announcement's display condition needs from the live player — their permission check,
     * world and gamemode names, and the per-viewer PlaceholderAPI bridge so a {@code %papi%} comparison expands the
     * same way the rendered announcement lines do. Mirrors the scoreboard/tablist renderers' condition context.
     */
    private static ConditionContext conditionContext(Player player) {
        return new ConditionContext(
                player::hasPermission,
                player.getWorld().getName(),
                player.getGameMode().name(),
                PlaceholderApiSupport.messageBridge(player.getUniqueId()));
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
                // The rotation cursor selects only over announcements WITHOUT an interval override; each override
                // announcement runs on its own independent timer driven by the AnnouncerTask.
                new NextAnnouncement(() -> settings.announcerConfig().rotating(), random),
                new BroadcastOptOut(optOutStore, notifier, kernel.events(), Clock.systemUTC()));
    }

    private static List<Listener> listeners(
            CommunicationServices services,
            InfoRegistry registry,
            BukkitInfoSender infoSender,
            CommunicationSettings settings,
            ChatLock chatLock,
            CommunicationNotifier notifier,
            ChannelBroadcaster channelBroadcaster,
            BroadcastOptOutStore optOutStore) {
        return List.of(
                new ConnectionMessageListener(services.resolveJoin(), services.resolveQuit(), settings),
                new DeathMessageListener(services.resolveDeath(), registry, infoSender, settings),
                // English-only project (a founding decision): vanilla advancement titles are translatable components,
                // so the listener renders them through the GlobalTranslator in this locale before flattening to text.
                new AdvancementMessageListener(
                        settings::advancementNotices,
                        channelBroadcaster,
                        optOutStore,
                        CommunicationWiring::isVanished,
                        Locale.ENGLISH),
                new ChatLockListener(chatLock, notifier));
    }

    /**
     * Whether {@code earner} is currently vanished, derived from Bukkit's own {@code Player#canSee} visibility graph —
     * the same soft-coupling seam messaging's {@code CanSeeVanishVisibility}, nametags, and teleport's {@code /tpa}
     * use. The presence module hides a vanished player from those without the vanish-see node, so an earner whom at
     * least one other online player cannot see is treated as vanished and their advancement is suppressed. When
     * presence is disabled nobody is hidden, {@code canSee} is always true, and the earner is never resolved as
     * vanished, so the feature degrades to "broadcast everyone's advancement" without depending on presence directly.
     * A solo earner (no other online player) is never vanished here — there is no one to be hidden from.
     */
    private static boolean isVanished(Player earner) {
        for (Player other : earner.getServer().getOnlinePlayers()) {
            if (!other.equals(earner) && !other.canSee(earner)) {
                return true;
            }
        }
        return false;
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

        /** Stop the announcer timer: flip the running flag and cancel every armed override loop. */
        public void stop() {
            running.set(false);
            announcer.stop();
        }
    }
}
