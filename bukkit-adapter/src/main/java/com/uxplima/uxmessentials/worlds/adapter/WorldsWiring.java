package com.uxplima.uxmessentials.worlds.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Server;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.worlds.CachedWorldRepository;
import com.uxplima.uxmessentials.persistence.worlds.WorldRepositories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.worlds.adapter.inbound.command.WorldCommands;
import com.uxplima.uxmessentials.worlds.adapter.outbound.BukkitWorldEngine;
import com.uxplima.uxmessentials.worlds.adapter.outbound.InFlightScheduler;
import com.uxplima.uxmessentials.worlds.adapter.outbound.InMemoryPendingDeletionRegistry;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.DeleteWorld;
import com.uxplima.uxmessentials.worlds.application.ImportWorld;
import com.uxplima.uxmessentials.worlds.application.ListWorlds;
import com.uxplima.uxmessentials.worlds.application.LoadWorld;
import com.uxplima.uxmessentials.worlds.application.ReconcileWorldsOnEnable;
import com.uxplima.uxmessentials.worlds.application.UnloadWorld;
import com.uxplima.uxmessentials.worlds.application.UnregisterWorld;
import com.uxplima.uxmessentials.worlds.application.WorldInfo;
import com.uxplima.uxmessentials.worlds.application.WorldNotifier;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
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

    public static Wired wire(ModuleContext ctx, Persistence persistence, Server server) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(server, "server");
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

        WorldsServices services = assemble(kernel, tracked, repository, notifier, engine, pending, settings, clock);
        ReconcileWorldsOnEnable reconcile = new ReconcileWorldsOnEnable(
                repository, engine, kernel.events(), clock, settings::autoAdoptLoaded, settings::autoLoadRegistered);

        List<CommandRegistration> commands = WorldCommands.all(services, kernel.messages());
        Runnable startReconcile = () -> kernel.scheduler().onGlobal(() -> {
            reconcile.run();
            repository.invalidateAll(); // reconciliation mutated rows; drop the warm snapshot so the next read reloads
            services.refreshImportableFolders();
        });
        return new Wired(commands, startReconcile, () -> awaitDrain(inFlight));
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
            Clock clock) {
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
        return new WorldsServices(
                createWorld,
                importWorld,
                loadWorld,
                unloadWorld,
                unregisterWorld,
                deleteWorld,
                listWorlds,
                worldInfo,
                repository,
                kernel.scheduler(),
                engine::onDiskWorldNames);
    }

    /** The wired worlds adapters handed back to bootstrap: commands, the reconcile kick, and the stop hook. */
    public record Wired(List<CommandRegistration> commands, Runnable startReconcile, Runnable stop) {
        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(startReconcile, "startReconcile");
            Objects.requireNonNull(stop, "stop");
        }
    }
}
