package com.uxplima.uxmessentials.worlds.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.bukkit.Server;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.worlds.CachedWorldRepository;
import com.uxplima.uxmessentials.persistence.worlds.WorldRepositories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.worlds.adapter.inbound.command.WorldCommands;
import com.uxplima.uxmessentials.worlds.adapter.inbound.listener.ForceGamemodeListener;
import com.uxplima.uxmessentials.worlds.adapter.inbound.listener.WorldAccessListener;
import com.uxplima.uxmessentials.worlds.adapter.inbound.listener.WorldPortalListener;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitGameRuleCatalog;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitWorldEngine;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitWorldSettingApplier;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitWorldTeleporter;
import com.uxplima.uxmessentials.worlds.adapter.outbound.ForcedWorldEntryMarker;
import com.uxplima.uxmessentials.worlds.adapter.outbound.InFlightScheduler;
import com.uxplima.uxmessentials.worlds.adapter.outbound.InMemoryPendingDeletionRegistry;
import com.uxplima.uxmessentials.worlds.adapter.outbound.WorldGeneratorResolver;
import com.uxplima.uxmessentials.worlds.application.ApplyWorldSettingsOnLoad;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.DeleteWorld;
import com.uxplima.uxmessentials.worlds.application.ImportWorld;
import com.uxplima.uxmessentials.worlds.application.ListWorlds;
import com.uxplima.uxmessentials.worlds.application.LoadWorld;
import com.uxplima.uxmessentials.worlds.application.ReconcileWorldsOnEnable;
import com.uxplima.uxmessentials.worlds.application.ResolvePortalDestination;
import com.uxplima.uxmessentials.worlds.application.SetGamerule;
import com.uxplima.uxmessentials.worlds.application.SetWorldAlias;
import com.uxplima.uxmessentials.worlds.application.SetWorldProperty;
import com.uxplima.uxmessentials.worlds.application.SetWorldSpawn;
import com.uxplima.uxmessentials.worlds.application.UnloadWorld;
import com.uxplima.uxmessentials.worlds.application.UnregisterWorld;
import com.uxplima.uxmessentials.worlds.application.WorldAccessPolicy;
import com.uxplima.uxmessentials.worlds.application.WorldInfo;
import com.uxplima.uxmessentials.worlds.application.WorldNotifier;
import com.uxplima.uxmessentials.worlds.application.WorldTeleportService;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog;
import com.uxplima.uxmessentials.worlds.application.port.WorldEntryFee;
import com.uxplima.uxmessentials.worlds.application.port.WorldSettingApplier;
import com.uxplima.uxmessentials.worlds.domain.event.WorldLoaded;
import com.uxplima.uxmessentials.worlds.domain.event.WorldSettingChanged;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the worlds context's adapters and use cases over the injected kernel ports, the persistence
 * DSL, and the Bukkit {@link Server}, and produces the Brigadier command list the plugin registers. This
 * is the one place the worlds context is wired — nothing else news up its classes.
 *
 * <p>The repository is the cached jOOQ adapter; its in-memory snapshot is warmed once on enable so every
 * later {@code /worlds} resolve and tab-complete is served from memory rather than a synchronous SQLite
 * read on the command thread. The enable-time reconcile runs on the global region thread (Bukkit world
 * handles require it), then invalidates the warmed snapshot — its adopt/auto-load phases mutated rows — so
 * the next read reloads, and refreshes the import-folder snapshot off-tick.
 */
@NullMarked
public final class WorldsWiring {

    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private WorldsWiring() {}

    public static Wired wire(
            ModuleContext ctx,
            Persistence persistence,
            Server server,
            InProcessDomainEventPublisher events,
            TeleportEngine teleportEngine,
            WorldEntryFee entryFee) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        Objects.requireNonNull(entryFee, "entryFee");
        KernelPorts kernel = ctx.kernel();

        CachedWorldRepository repository = WorldRepositories.cachedConcrete(persistence);
        repository.all(); // warm the in-memory snapshot once on enable (tick-safe reads thereafter)

        WorldNotifier notifier = new WorldNotifier(kernel.messages(), kernel.messageSink());
        WorldsSettings settings = new WorldsSettings(ctx.config());
        WorldGeneratorResolver resolver = new WorldGeneratorResolver(
                settings.flatLayers(), settings.voidBiome(), settings.flatBiome(), kernel.log());
        BukkitWorldEngine engine = new BukkitWorldEngine(server, kernel.log(), resolver);
        InMemoryPendingDeletionRegistry pending = new InMemoryPendingDeletionRegistry();
        Clock clock = Clock.systemUTC();

        AtomicInteger inFlight = new AtomicInteger();
        Scheduler tracked = new InFlightScheduler(kernel.scheduler(), inFlight);

        GameRuleCatalog ruleCatalog = new BukkitGameRuleCatalog();
        WorldSettingApplier applier = new BukkitWorldSettingApplier(server, ruleCatalog, kernel.log());
        ApplyWorldSettingsOnLoad onLoad = new ApplyWorldSettingsOnLoad(repository, applier);

        WorldAccessPolicy policy = new WorldAccessPolicy(kernel.permissions(), engine);
        // One marker shared between the teleporter (which marks staff /worlds tp and login-redirect hand-offs)
        // and the access listener (which consumes the mark to exempt those worlds-initiated teleports).
        ForcedWorldEntryMarker forcedEntries = new ForcedWorldEntryMarker();
        BukkitWorldTeleporter teleporter = new BukkitWorldTeleporter(teleportEngine, forcedEntries);
        WorldTeleportService worldTeleport = new WorldTeleportService(
                repository,
                engine,
                policy,
                teleporter,
                entryFee,
                kernel.permissions(),
                kernel.events(),
                notifier,
                tracked);
        WorldAccessListener accessListener = new WorldAccessListener(
                repository,
                policy,
                worldTeleport,
                engine,
                kernel.events(),
                kernel.scheduler(),
                notifier,
                forcedEntries,
                settings.redirectOnRestrictedJoin());
        ResolvePortalDestination resolvePortal = new ResolvePortalDestination(repository);
        WorldPortalListener portalListener = new WorldPortalListener(resolvePortal, server, server.getLogger());

        WorldsServices services = assemble(
                kernel, tracked, repository, notifier, engine, pending, settings, clock, ruleCatalog, worldTeleport);
        ReconcileWorldsOnEnable reconcile = new ReconcileWorldsOnEnable(
                repository, engine, kernel.events(), clock, settings::autoAdoptLoaded, settings::autoLoadRegistered);

        // Re-apply a world's stored settings to the live world on load and after any setting change, on the
        // global region thread (the applier touches Bukkit world handles). Subscribed for the module's lifetime
        // and dropped on stop so a reload never leaves a stale listener firing against torn-down state.
        Consumer<DomainEvent> applySubscriber = event -> {
            if (event instanceof WorldLoaded loaded) {
                kernel.scheduler().onGlobal(() -> onLoad.apply(loaded.name()));
            } else if (event instanceof WorldSettingChanged changed) {
                kernel.scheduler().onGlobal(() -> onLoad.apply(changed.name()));
            }
        };
        events.subscribe(applySubscriber);

        List<Listener> listeners =
                List.of(new ForceGamemodeListener(repository, kernel.scheduler()), accessListener, portalListener);
        List<CommandRegistration> commands = WorldCommands.all(services, kernel.messages());
        Runnable startReconcile = () -> kernel.scheduler().onGlobal(() -> {
            reconcile.run();
            repository.invalidateAll(); // reconciliation mutated rows; drop the warm snapshot so the next read reloads
            services.refreshImportableFolders();
        });
        return new Wired(
                commands,
                listeners,
                startReconcile,
                () -> {
                    events.unsubscribe(applySubscriber);
                    awaitDrain(inFlight);
                },
                resolver);
    }

    /** Spin-wait until the off-tick write tails finish, bounded by {@link #DRAIN_TIMEOUT}, on stop/reload. */
    private static void awaitDrain(AtomicInteger inFlight) {
        long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
        while (inFlight.get() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    private static WorldsServices assemble(
            KernelPorts kernel,
            Scheduler scheduler,
            com.uxplima.uxmessentials.worlds.application.port.WorldRepository repository,
            WorldNotifier notifier,
            BukkitWorldEngine engine,
            InMemoryPendingDeletionRegistry pending,
            WorldsSettings settings,
            Clock clock,
            GameRuleCatalog ruleCatalog,
            WorldTeleportService worldTeleport) {
        CreateWorld createWorld = new CreateWorld(repository, engine, notifier, kernel.events(), scheduler, clock);
        ImportWorld importWorld = new ImportWorld(repository, engine, notifier, kernel.events(), scheduler, clock);
        LoadWorld loadWorld = new LoadWorld(repository, engine, notifier, kernel.events(), scheduler);
        UnloadWorld unloadWorld = new UnloadWorld(engine, notifier, kernel.events(), settings::protectDefaultWorld);
        UnregisterWorld unregisterWorld = new UnregisterWorld(repository, notifier, kernel.events(), scheduler);
        DeleteWorld deleteWorld = new DeleteWorld(
                repository,
                engine,
                pending,
                notifier,
                kernel.events(),
                scheduler,
                clock,
                settings::protectDefaultWorld);
        ListWorlds listWorlds = new ListWorlds(repository, engine);
        WorldInfo worldInfo = new WorldInfo(repository);
        SetWorldProperty setProperty = new SetWorldProperty(repository, notifier, kernel.events(), scheduler);
        SetGamerule setGamerule = new SetGamerule(repository, ruleCatalog, notifier, kernel.events(), scheduler);
        SetWorldSpawn setSpawn = new SetWorldSpawn(repository, notifier, kernel.events(), scheduler);
        SetWorldAlias setAlias = new SetWorldAlias(repository, notifier, kernel.events(), scheduler);
        return new WorldsServices(
                createWorld,
                importWorld,
                loadWorld,
                unloadWorld,
                unregisterWorld,
                deleteWorld,
                listWorlds,
                worldInfo,
                setProperty,
                setGamerule,
                setSpawn,
                setAlias,
                ruleCatalog,
                repository,
                kernel.scheduler(),
                engine::onDiskWorldNames,
                worldTeleport);
    }

    /**
     * The wired worlds adapters handed back to bootstrap: commands, listeners, the reconcile kick, the
     * stop hook (which unsubscribes the live-apply listener and drains in-flight off-tick writes), and the
     * built-in generator resolver. The resolver is non-null whenever worlds wires (it is always built from
     * the config); bootstrap captures it onto the holder the plugin retains so {@code getDefaultWorldGenerator}
     * can serve {@code generator: uxmEssentials:void|flat} worlds loaded from server.properties.
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            Runnable startReconcile,
            Runnable stop,
            WorldGeneratorResolver generatorResolver) {
        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(startReconcile, "startReconcile");
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(generatorResolver, "generatorResolver");
        }
    }
}
