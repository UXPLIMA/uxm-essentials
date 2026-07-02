package com.uxplima.uxmessentials.teleport.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.teleport.RtpPoolStores;
import com.uxplima.uxmessentials.persistence.teleport.SpawnDirectories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.teleport.adapter.inbound.command.TeleportCommands;
import com.uxplima.uxmessentials.teleport.adapter.inbound.command.TpSettingsCommand;
import com.uxplima.uxmessentials.teleport.adapter.inbound.gui.TeleportSettingsView;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.ArrivalGraceGuard;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.FirstJoinRtpListener;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.RequestExpirySweep;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.RespawnListener;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.TeleportListeners;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.WarmupTracker;
import com.uxplima.uxmessentials.teleport.adapter.outbound.AsyncTeleportExecutor;
import com.uxplima.uxmessentials.teleport.adapter.outbound.BukkitChunkAccess;
import com.uxplima.uxmessentials.teleport.adapter.outbound.InMemoryBackLocationStore;
import com.uxplima.uxmessentials.teleport.adapter.outbound.InMemoryRequestRegistry;
import com.uxplima.uxmessentials.teleport.adapter.outbound.PdcTeleportFlags;
import com.uxplima.uxmessentials.teleport.adapter.outbound.PrewarmedSafeLocationQueue;
import com.uxplima.uxmessentials.teleport.adapter.outbound.RtpPoolSettings;
import com.uxplima.uxmessentials.teleport.adapter.outbound.RtpPoolWarmup;
import com.uxplima.uxmessentials.teleport.adapter.outbound.RtpWorldSettings;
import com.uxplima.uxmessentials.teleport.adapter.outbound.TeleportArrivalEffects;
import com.uxplima.uxmessentials.teleport.adapter.outbound.TeleportArrivalHud;
import com.uxplima.uxmessentials.teleport.adapter.outbound.TrackingWarmups;
import com.uxplima.uxmessentials.teleport.adapter.outbound.VanillaFallbackSpawnDirectory;
import com.uxplima.uxmessentials.teleport.application.AcceptTeleport;
import com.uxplima.uxmessentials.teleport.application.AsyncSafeLocationFinder;
import com.uxplima.uxmessentials.teleport.application.BudgetedSafeSearch;
import com.uxplima.uxmessentials.teleport.application.CaptureBack;
import com.uxplima.uxmessentials.teleport.application.ListPendingRequests;
import com.uxplima.uxmessentials.teleport.application.PlayerNotifier;
import com.uxplima.uxmessentials.teleport.application.RequestTeleport;
import com.uxplima.uxmessentials.teleport.application.ResolveRespawn;
import com.uxplima.uxmessentials.teleport.application.ResolveRtp;
import com.uxplima.uxmessentials.teleport.application.ResolveSpawn;
import com.uxplima.uxmessentials.teleport.application.RtpPoolPrewarm;
import com.uxplima.uxmessentials.teleport.application.RtpPoolSink;
import com.uxplima.uxmessentials.teleport.application.RtpPoolWriter;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace;
import com.uxplima.uxmessentials.teleport.application.port.RtpPoolStore;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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

    // A couple of ticks between rescheduled RTP search attempts — enough to slice a long search across ticks so
    // it never fires every candidate at once or monopolises an async worker.
    private static final Duration RTP_RETRY_INTERVAL = Duration.ofMillis(100);

    private TeleportWiring() {}

    /** Build the teleport adapters and use cases from {@code ctx}, ready to register with the plugin. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            GuiLayouts guiLayouts,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            TeleportFee fee) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(guiRegistry, "guiRegistry");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(fee, "fee");
        ConfigStore config = ctx.config();
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();
        AtomicBoolean running = new AtomicBoolean(true);

        TeleportSettings settings = new TeleportSettings(config);
        PlayerNotifier notifier = new PlayerNotifier(kernel.messages(), kernel.messageSink());
        WarmupTracker warmupTracker = new WarmupTracker();
        // The jail gate forwards to NEVER until the moderation context lands and rebinds it (soft couple).
        MutableJailGate jailGate = new MutableJailGate();
        // The home-respawn seam resolves to empty until the homes context lands and rebinds it (soft couple),
        // so a HOME step in a configured respawn chain falls through whenever homes is disabled.
        MutableHomeRespawnLocator homeRespawnLocator = new MutableHomeRespawnLocator();
        SpawnDirectory spawns = spawns(plugin, persistence);
        RtpBundle rtp = buildRtp(plugin, kernel, config, settings, persistence, running);
        // The post-arrival grace shields an /rtp landing (Resistance + Slow-Falling + a no-fall-damage window);
        // it is both the engine's ArrivalGrace port and the fall-damage listener, so it is registered below.
        ArrivalGraceGuard graceGuard =
                new ArrivalGraceGuard(plugin.getServer(), kernel.scheduler(), settings::arrivalGrace, clock);
        TeleportServices services = assemble(
                plugin,
                kernel,
                settings,
                notifier,
                warmupTracker,
                jailGate,
                spawns,
                clock,
                rtp.queue(),
                fee,
                graceGuard);
        RequestExpirySweep sweep = new RequestExpirySweep(
                kernel.scheduler(), services.requests(), services.acceptTeleport(), kernel.log(), running::get);
        RespawnListener respawnListener = new RespawnListener(
                new ResolveRespawn(settings),
                spawns,
                homeRespawnLocator,
                plugin.getServer(),
                services.rtpQueue(),
                graceGuard,
                settings::rtpOnRespawnWorlds);
        FirstJoinRtpListener firstJoinListener =
                new FirstJoinRtpListener(services.resolveRtp(), settings::rtpOnFirstJoin);
        // The per-player settings panel reuses the SP0 GUI framework over the shared catalog and the data-folder
        // layout loader. It reads and writes the same TeleportFlags the /tptoggle and /tpauto commands do, so
        // /tpsettings, the panel, and the commands all see one switch. The teleport entry on the /uxmess gui hub
        // opens the same panel for an admin (gated on uxmessentials.teleport.gui).
        GuiText guiText = new GuiText(kernel.messages());
        TeleportSettingsView settingsView = new TeleportSettingsView(
                guiText, kernel.scheduler(), guiLayouts, kernel.messages(), services.flags(), menus);
        guiRegistry.register(new ManagementGuiEntry(
                "teleport",
                TeleportMessageKey.GUI_SETTINGS_TITLE,
                org.bukkit.Material.ENDER_PEARL,
                "uxmessentials.teleport.gui",
                settingsView::open));
        List<CommandRegistration> commands =
                new java.util.ArrayList<>(TeleportCommands.all(services, kernel.messages()));
        commands.add(new TpSettingsCommand(services, kernel.messages(), settingsView));
        return new Wired(
                services,
                commands,
                listeners(services, config, respawnListener, firstJoinListener, graceGuard),
                sweep,
                jailGate,
                homeRespawnLocator,
                running,
                rtp.warmup(),
                graceGuard);
    }

    private static TeleportServices assemble(
            Plugin plugin,
            KernelPorts kernel,
            TeleportSettings settings,
            PlayerNotifier notifier,
            WarmupTracker warmupTracker,
            MutableJailGate jailGate,
            SpawnDirectory spawns,
            Clock clock,
            PrewarmedSafeLocationQueue rtpQueue,
            TeleportFee fee,
            ArrivalGrace grace) {
        InMemoryBackLocationStore backStore = new InMemoryBackLocationStore();
        InMemoryRequestRegistry requests = new InMemoryRequestRegistry(settings.singleRequestDisplace());
        PdcTeleportFlags flags = new PdcTeleportFlags(plugin);
        TeleportArrivalHud arrivalHud =
                new TeleportArrivalHud(kernel.messages(), plugin.getServer(), settings, kernel.scheduler());
        TeleportArrivalEffects arrivalEffects =
                new TeleportArrivalEffects(plugin.getServer(), settings, kernel.scheduler());
        TeleportExecutor executor = new AsyncTeleportExecutor(
                kernel.scheduler(),
                backStore,
                kernel.events(),
                kernel.log(),
                clock,
                settings::teleportToCenter,
                arrivalHud,
                arrivalEffects);
        Warmups warmups = new TrackingWarmups(
                kernel.warmups(), warmupTracker, settings::cancelToggles, kernel.permissions(), clock);
        TeleportEngine engine = new TeleportEngine(
                kernel.cooldowns(), warmups, executor, notifier, kernel.events(), settings, jailGate, fee, grace);
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
                .scheduler(kernel.scheduler())
                .requestTeleport(
                        new RequestTeleport(requests, flags, notifier, kernel.events(), settings, jailGate, clock))
                .acceptTeleport(new AcceptTeleport(requests, engine, notifier, kernel.events(), clock))
                .listPendingRequests(new ListPendingRequests(requests, notifier))
                .captureBack(new CaptureBack(backStore, engine, notifier, kernel.events(), clock))
                .resolveRtp(new ResolveRtp(rtpQueue, kernel.worldLookup(), engine, notifier, settings))
                .resolveSpawn(new ResolveSpawn(spawns, kernel.worldLookup(), engine, notifier))
                .build();
    }

    /**
     * Assemble the RTP engine: the async finder, the budgeted search, the in-memory pre-warmed queue, and — when the
     * persisted pool is enabled in config — the durable {@link RtpPoolStore}, the persist-on-validate {@link
     * RtpPoolWriter} the queue records through, and the enable-time {@link RtpPoolWarmup} that pre-warms each world's
     * queue from disk. With the pool disabled the sink is {@link RtpPoolSink#NONE} and there is no warmup, so the
     * queue runs purely in memory.
     */
    private static RtpBundle buildRtp(
            Plugin plugin,
            KernelPorts kernel,
            ConfigStore config,
            TeleportSettings settings,
            Persistence persistence,
            AtomicBoolean running) {
        // The safe-search probe loads each candidate's chunk asynchronously through BukkitChunkAccess, so no RTP
        // probe generates a far chunk on a tick thread and every probed-but-unserved chunk is released again. The
        // budgeted search wraps the finder so a single search terminates within its budget and tick-slices its
        // retries through the scheduler — no worker blocks on a candidate any more.
        AsyncSafeLocationFinder finder = new AsyncSafeLocationFinder(
                new BukkitChunkAccess(plugin.getServer(), kernel.log()),
                settings.safeSearchPolicy(),
                Clock.systemUTC());
        BudgetedSafeSearch search =
                new BudgetedSafeSearch(finder, kernel.scheduler(), Clock.systemUTC(), RTP_RETRY_INTERVAL);
        RtpWorldSettings worldSettings = RtpWorldSettings.from(config);
        RtpPoolSettings poolSettings = RtpPoolSettings.from(config);
        if (!poolSettings.persist()) {
            PrewarmedSafeLocationQueue queue = new PrewarmedSafeLocationQueue(
                    kernel.scheduler(), search, worldSettings, kernel.log(), running::get, RtpPoolSink.NONE);
            return new RtpBundle(queue, null);
        }
        RtpPoolStore store = RtpPoolStores.cached(persistence, poolSettings.maxPerWorld(), Clock.systemUTC());
        RtpPoolWriter writer = new RtpPoolWriter(store, kernel.scheduler(), kernel.log());
        PrewarmedSafeLocationQueue queue = new PrewarmedSafeLocationQueue(
                kernel.scheduler(), search, worldSettings, kernel.log(), running::get, writer);
        RtpPoolPrewarm prewarm =
                new RtpPoolPrewarm(store, finder, kernel.scheduler(), kernel.log(), RTP_RETRY_INTERVAL);
        RtpPoolWarmup warmup = new RtpPoolWarmup(
                store,
                prewarm,
                queue,
                plugin.getServer(),
                kernel.scheduler(),
                poolSettings,
                worldSettings.targetSize(),
                kernel.log(),
                running::get);
        return new RtpBundle(queue, warmup);
    }

    /** The wired RTP engine: the servable queue and, when the pool is persisted, the enable-time warmup. */
    private record RtpBundle(
            PrewarmedSafeLocationQueue queue, @Nullable RtpPoolWarmup warmup) {}

    private static SpawnDirectory spawns(Plugin plugin, Persistence persistence) {
        // The durable jOOQ store holds the per-world spawns, the main spawn, named spawns and mirror
        // redirects; the decorator adds the vanilla world spawn as the bottom-of-chain last resort so
        // /spawn answers on a fresh server before any /setspawn.
        return new VanillaFallbackSpawnDirectory(SpawnDirectories.jooq(persistence), plugin.getServer());
    }

    private static List<Listener> listeners(
            TeleportServices services,
            ConfigStore config,
            RespawnListener respawnListener,
            FirstJoinRtpListener firstJoinListener,
            ArrivalGraceGuard graceGuard) {
        return List.of(
                new TeleportListeners(
                        services.warmupTracker(),
                        services.captureBack(),
                        config,
                        services.settings()::backCapturePolicy),
                respawnListener,
                firstJoinListener,
                graceGuard);
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
     * @param jailGate the rebindable jail gate moderation rebinds when it lands
     * @param homeRespawnLocator the rebindable home-respawn seam homes rebinds when it lands
     * @param running the flag flipped false on stop so the sweep and refill loops exit
     * @param poolWarmup the enable-time RTP pool pre-warm, or {@code null} when the persisted pool is disabled
     * @param graceGuard the post-arrival grace / fall-damage guard, cleared on stop
     */
    public record Wired(
            TeleportServices services,
            List<CommandRegistration> commands,
            List<Listener> listeners,
            RequestExpirySweep expirySweep,
            MutableJailGate jailGate,
            MutableHomeRespawnLocator homeRespawnLocator,
            AtomicBoolean running,
            @Nullable RtpPoolWarmup poolWarmup,
            ArrivalGraceGuard graceGuard) {

        public Wired {
            Objects.requireNonNull(services, "services");
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(expirySweep, "expirySweep");
            Objects.requireNonNull(jailGate, "jailGate");
            Objects.requireNonNull(homeRespawnLocator, "homeRespawnLocator");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(graceGuard, "graceGuard");
        }

        /**
         * Arm the expiry sweep and, when the persisted pool is enabled, pre-warm each world's RTP queue from disk;
         * call after the listeners and commands are registered.
         */
        public void startBackgroundWork() {
            expirySweep.start();
            if (poolWarmup != null) {
                poolWarmup.start();
            }
        }

        /** Flip the running flag and drain the in-memory stores. Called on module stop. */
        public void stop() {
            running.set(false);
            services.drain();
            graceGuard.clear();
        }
    }
}
