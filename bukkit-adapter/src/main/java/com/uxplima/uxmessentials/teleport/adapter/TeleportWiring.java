package com.uxplima.uxmessentials.teleport.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.teleport.adapter.inbound.command.TeleportCommands;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.RequestExpirySweep;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.TeleportListeners;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.WarmupTracker;
import com.uxplima.uxmessentials.teleport.adapter.outbound.AsyncTeleportExecutor;
import com.uxplima.uxmessentials.teleport.adapter.outbound.BukkitSpawnDirectory;
import com.uxplima.uxmessentials.teleport.adapter.outbound.InMemoryBackLocationStore;
import com.uxplima.uxmessentials.teleport.adapter.outbound.InMemoryRequestRegistry;
import com.uxplima.uxmessentials.teleport.adapter.outbound.PdcTeleportFlags;
import com.uxplima.uxmessentials.teleport.adapter.outbound.PrewarmedSafeLocationQueue;
import com.uxplima.uxmessentials.teleport.adapter.outbound.RtpWorldSettings;
import com.uxplima.uxmessentials.teleport.adapter.outbound.SafeSearchValidator;
import com.uxplima.uxmessentials.teleport.adapter.outbound.TrackingWarmups;
import com.uxplima.uxmessentials.teleport.application.AcceptTeleport;
import com.uxplima.uxmessentials.teleport.application.CaptureBack;
import com.uxplima.uxmessentials.teleport.application.PlayerNotifier;
import com.uxplima.uxmessentials.teleport.application.RequestTeleport;
import com.uxplima.uxmessentials.teleport.application.ResolveRtp;
import com.uxplima.uxmessentials.teleport.application.ResolveSpawn;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the teleport context's adapters and use cases over the injected kernel ports, and produces
 * everything the plugin must register: the Brigadier command list, the move/death/quit listener, and the
 * TTL expiry sweep. This is the one place the teleport context is wired — nothing else news up its
 * classes. The {@code Plugin} handle stays inside bootstrap; the adapters take only the {@code Plugin}
 * interface and the kernel ports.
 *
 * <p>The warmup port is wrapped in {@link TrackingWarmups} so every warmup the engine begins is registered
 * with the {@link WarmupTracker}, giving the move-cancels-warmup listener the live handle to cancel.
 */
@NullMarked
public final class TeleportWiring {

    private TeleportWiring() {}

    /** Build the teleport adapters and use cases from {@code ctx}, ready to register with the plugin. */
    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        ConfigStore config = ctx.config();
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();
        AtomicBoolean running = new AtomicBoolean(true);

        TeleportSettings settings = new TeleportSettings(config);
        PlayerNotifier notifier = new PlayerNotifier(kernel.messages(), kernel.messageSink());
        WarmupTracker warmupTracker = new WarmupTracker();
        // The jail gate forwards to NEVER until the moderation context lands and rebinds it (soft couple).
        MutableJailGate jailGate = new MutableJailGate();
        TeleportServices services =
                assemble(plugin, kernel, config, settings, notifier, warmupTracker, jailGate, clock, running);
        RequestExpirySweep sweep = new RequestExpirySweep(
                kernel.scheduler(), services.requests(), services.acceptTeleport(), running::get);
        return new Wired(
                services,
                TeleportCommands.all(services, kernel.messages()),
                listeners(services, config),
                sweep,
                jailGate,
                running);
    }

    private static TeleportServices assemble(
            Plugin plugin,
            KernelPorts kernel,
            ConfigStore config,
            TeleportSettings settings,
            PlayerNotifier notifier,
            WarmupTracker warmupTracker,
            MutableJailGate jailGate,
            Clock clock,
            AtomicBoolean running) {
        InMemoryBackLocationStore backStore = new InMemoryBackLocationStore();
        InMemoryRequestRegistry requests = new InMemoryRequestRegistry(settings.singleRequestDisplace());
        PdcTeleportFlags flags = new PdcTeleportFlags(plugin);
        TeleportExecutor executor = new AsyncTeleportExecutor(
                kernel.scheduler(), backStore, kernel.events(), kernel.log(), clock, settings::teleportToCenter);
        PrewarmedSafeLocationQueue rtpQueue = rtpQueue(kernel, config, settings, running);
        Warmups warmups = new TrackingWarmups(kernel.warmups(), warmupTracker, settings::cancelToggles);
        TeleportEngine engine = new TeleportEngine(
                kernel.cooldowns(), warmups, executor, notifier, kernel.events(), settings, jailGate);
        return new TeleportServices.Builder()
                .engine(engine)
                .notifier(notifier)
                .settings(settings)
                .warmupTracker(warmupTracker)
                .requests(requests)
                .backStore(backStore)
                .flags(flags)
                .rtpQueue(rtpQueue)
                .executor(executor)
                .players(kernel.playerLookup())
                .worlds(kernel.worldLookup())
                .requestTeleport(
                        new RequestTeleport(requests, flags, notifier, kernel.events(), settings, jailGate, clock))
                .acceptTeleport(new AcceptTeleport(requests, engine, notifier, kernel.events(), clock))
                .captureBack(new CaptureBack(backStore, engine, notifier, kernel.events(), clock))
                .resolveRtp(new ResolveRtp(rtpQueue, kernel.worldLookup(), engine, notifier, settings))
                .resolveSpawn(new ResolveSpawn(spawns(), kernel.worldLookup(), engine, notifier))
                .build();
    }

    private static PrewarmedSafeLocationQueue rtpQueue(
            KernelPorts kernel, ConfigStore config, TeleportSettings settings, AtomicBoolean running) {
        SafeSearchValidator validator =
                new SafeSearchValidator(kernel.scheduler(), settings.safeSearchPolicy(), Clock.systemUTC());
        return new PrewarmedSafeLocationQueue(
                kernel.scheduler(), validator, RtpWorldSettings.from(config), kernel.log(), running::get);
    }

    private static BukkitSpawnDirectory spawns() {
        // Spawn-mirror rules would be parsed from teleport.conf here once the mirror config block lands;
        // an empty set is the working default (no world redirects).
        return new BukkitSpawnDirectory(List.of());
    }

    private static List<Listener> listeners(TeleportServices services, ConfigStore config) {
        return List.of(new TeleportListeners(
                services.warmupTracker(), services.captureBack(), config, services.settings()::backCapturePolicy));
    }

    /**
     * Everything the teleport module contributes once wired: the services (for stop-time drain), the
     * Brigadier commands, the Bukkit listeners, the expiry sweep, and the {@code running} flag the async
     * loops observe.
     *
     * @param services the constructed use cases and in-memory stores
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     * @param expirySweep the self-rescheduling TTL sweep, armed by the caller
     * @param running the flag flipped false on stop so the sweep and refill loops exit
     */
    public record Wired(
            TeleportServices services,
            List<CommandRegistration> commands,
            List<Listener> listeners,
            RequestExpirySweep expirySweep,
            MutableJailGate jailGate,
            AtomicBoolean running) {

        public Wired {
            Objects.requireNonNull(services, "services");
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(expirySweep, "expirySweep");
            Objects.requireNonNull(jailGate, "jailGate");
            Objects.requireNonNull(running, "running");
        }

        /** Arm the expiry sweep; call after the listeners and commands are registered. */
        public void startBackgroundWork() {
            expirySweep.start();
        }

        /** Flip the running flag and drain the in-memory stores. Called on module stop. */
        public void stop() {
            running.set(false);
            services.drain();
        }
    }
}
