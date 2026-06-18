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
import com.uxplima.uxmessentials.worlds.adapter.inbound.command.WorldCommands;
import com.uxplima.uxmessentials.worlds.adapter.inbound.listener.ForceGamemodeListener;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitGameRuleCatalog;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitWorldEngine;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitWorldSettingApplier;
import com.uxplima.uxmessentials.worlds.adapter.outbound.InFlightScheduler;
import com.uxplima.uxmessentials.worlds.adapter.outbound.InMemoryPendingDeletionRegistry;
import com.uxplima.uxmessentials.worlds.application.ApplyWorldSettingsOnLoad;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.DeleteWorld;
import com.uxplima.uxmessentials.worlds.application.ImportWorld;
import com.uxplima.uxmessentials.worlds.application.ListWorlds;
import com.uxplima.uxmessentials.worlds.application.LoadWorld;
import com.uxplima.uxmessentials.worlds.application.ReconcileWorldsOnEnable;
import com.uxplima.uxmessentials.worlds.application.SetGamerule;
import com.uxplima.uxmessentials.worlds.application.SetWorldAlias;
import com.uxplima.uxmessentials.worlds.application.SetWorldProperty;
import com.uxplima.uxmessentials.worlds.application.SetWorldSpawn;
import com.uxplima.uxmessentials.worlds.application.UnloadWorld;
import com.uxplima.uxmessentials.worlds.application.UnregisterWorld;
import com.uxplima.uxmessentials.worlds.application.WorldInfo;
import com.uxplima.uxmessentials.worlds.application.WorldNotifier;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog;
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
            ModuleContext ctx, Persistence persistence, Server server, InProcessDomainEventPublisher events) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(events, "events");
        KernelPorts kernel = ctx.kernel();

        CachedWorldRepository repository = WorldRepositories.cachedConcrete(persistence);
        repository.all(); // warm the in-memory snapshot once on enable (tick-safe reads thereafter)

        WorldNotifier notifier = new WorldNotifier(kernel.messages(), kernel.messageSink());
        WorldsSettings settings = new WorldsSettings(ctx.config());
        BukkitWorldEngine engine = new BukkitWorldEngine(server, kernel.log());
        InMemoryPendingDeletionRegistry pending = new InMemoryPendingDeletionRegistry();
        Clock clock = Clock.systemUTC();

        AtomicInteger inFlight = new AtomicInteger();
        Scheduler tracked = new InFlightScheduler(kernel.scheduler(), inFlight);

        GameRuleCatalog ruleCatalog = new BukkitGameRuleCatalog();
        WorldSettingApplier applier = new BukkitWorldSettingApplier(server, ruleCatalog, kernel.log());
        ApplyWorldSettingsOnLoad onLoad = new ApplyWorldSettingsOnLoad(repository, applier);

        WorldsServices services =
                assemble(kernel, tracked, repository, notifier, engine, pending, settings, clock, ruleCatalog);
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

        List<Listener> listeners = List.of(new ForceGamemodeListener(repository, kernel.scheduler()));
        List<CommandRegistration> commands = WorldCommands.all(services, kernel.messages());
        Runnable startReconcile = () -> kernel.scheduler().onGlobal(() -> {
            reconcile.run();
            repository.invalidateAll(); // reconciliation mutated rows; drop the warm snapshot so the next read reloads
            services.refreshImportableFolders();
        });
        return new Wired(commands, listeners, startReconcile, () -> {
            events.unsubscribe(applySubscriber);
            awaitDrain(inFlight);
        });
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
            GameRuleCatalog ruleCatalog) {
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
                engine::onDiskWorldNames);
    }

    /**
     * The wired worlds adapters handed back to bootstrap: commands, listeners, the reconcile kick, and the
     * stop hook (which unsubscribes the live-apply listener and drains in-flight off-tick writes).
     */
    public record Wired(
            List<CommandRegistration> commands, List<Listener> listeners, Runnable startReconcile, Runnable stop) {
        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(startReconcile, "startReconcile");
            Objects.requireNonNull(stop, "stop");
        }
    }
}
