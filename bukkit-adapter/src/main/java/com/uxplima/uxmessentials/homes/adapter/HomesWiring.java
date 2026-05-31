package com.uxplima.uxmessentials.homes.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.homes.adapter.inbound.command.HomeCommands;
import com.uxplima.uxmessentials.homes.adapter.outbound.TeleportHomeAdapter;
import com.uxplima.uxmessentials.homes.application.DelHome;
import com.uxplima.uxmessentials.homes.application.HomeAdmin;
import com.uxplima.uxmessentials.homes.application.HomeNotifier;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.ListHomes;
import com.uxplima.uxmessentials.homes.application.MoveHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.SetHome;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.persistence.homes.CachedHomeRepository;
import com.uxplima.uxmessentials.persistence.homes.HomeRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.HomeSync;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the homes context's adapters and use cases over the injected kernel ports, the persistence
 * DSL, and the teleport context's engine, and produces the Brigadier command list the plugin registers.
 * This is the one place the homes context is wired — nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a Caffeine read-cache decorator (write-through at the
 * delegate, invalidate in the cache). The teleporter delegates execution to the teleport context — homes
 * never re-implements movement — which is why the wiring receives the already-constructed
 * {@link TeleportEngine}.
 */
@NullMarked
public final class HomesWiring {

    private static final int DEFAULT_HOME_LIMIT = 3;

    private HomesWiring() {}

    /** Build the homes adapters and use cases from {@code ctx}, the {@code persistence} DSL, and the engine. */
    public static Wired wire(ModuleContext ctx, Persistence persistence, TeleportEngine teleportEngine, Bus bus) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        Objects.requireNonNull(bus, "bus");
        KernelPorts kernel = ctx.kernel();
        // The cached repository is the read accelerator; the bus listener invalidates exactly the owner a peer
        // reports changed, and the broadcasting decorator announces this backend's own writes to peers.
        CachedHomeRepository cached = HomeRepositories.cachedConcrete(persistence);
        bus.registry().register(HomeSync.listener(cached));
        HomeRepository repository = HomeSync.repository(cached, bus.publisher());
        HomeNotifier notifier = new HomeNotifier(kernel.messages(), kernel.messageSink());
        HomeQuota quota = new HomeQuota(kernel.permissions(), defaultLimit(ctx));
        HomeTeleporter teleporter = new TeleportHomeAdapter(teleportEngine);
        HomeServices services = assemble(ctx, repository, notifier, quota, teleporter);
        return new Wired(HomeCommands.all(services, kernel.messages()));
    }

    private static HomeServices assemble(
            ModuleContext ctx,
            HomeRepository repository,
            HomeNotifier notifier,
            HomeQuota quota,
            HomeTeleporter teleporter) {
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();
        return new HomeServices(
                new SetHome(repository, quota, notifier, kernel.events(), clock),
                new DelHome(repository, notifier, kernel.events()),
                new ListHomes(repository, notifier),
                new TeleportHome(repository, teleporter, notifier),
                new RenameHome(repository, notifier),
                new MoveHome(repository, notifier),
                new HomeAdmin(repository, teleporter, notifier, kernel.events()),
                kernel.playerLookup());
    }

    private static int defaultLimit(ModuleContext ctx) {
        return Math.max(0, ctx.config().getInt("default-limit", DEFAULT_HOME_LIMIT));
    }

    /**
     * Everything the homes module contributes once wired: the Brigadier commands. The homes context holds
     * no repeating scheduled work and no in-memory store beyond the repository cache, so there is nothing
     * to drain on stop — the module's {@code stop()} clears its own bookkeeping and the cache expires.
     *
     * @param commands the Brigadier command registrations to publish
     */
    public record Wired(List<CommandRegistration> commands) {

        public Wired {
            commands = List.copyOf(commands);
        }
    }
}
