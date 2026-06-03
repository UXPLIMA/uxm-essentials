package com.uxplima.uxmessentials.homes.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;

import com.uxplima.uxmessentials.homes.adapter.inbound.command.HomeCommands;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeMenuView;
import com.uxplima.uxmessentials.homes.adapter.outbound.TeleportHomeAdapter;
import com.uxplima.uxmessentials.homes.application.DelHome;
import com.uxplima.uxmessentials.homes.application.HomeAdmin;
import com.uxplima.uxmessentials.homes.application.HomeNotifier;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.ListHomes;
import com.uxplima.uxmessentials.homes.application.MoveHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.SetHome;
import com.uxplima.uxmessentials.homes.application.SetMainHome;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.application.port.MainHomePreference;
import com.uxplima.uxmessentials.persistence.homes.CachedHomeRepository;
import com.uxplima.uxmessentials.persistence.homes.HomeRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
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
    public static Wired wire(
            ModuleContext ctx, Persistence persistence, TeleportEngine teleportEngine, Bus bus, GuiLayouts guiLayouts) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        KernelPorts kernel = ctx.kernel();
        // The cached repository is the read accelerator; the bus listener invalidates exactly the owner a peer
        // reports changed, and the broadcasting decorator announces this backend's own writes to peers.
        CachedHomeRepository cached = HomeRepositories.cachedConcrete(persistence);
        bus.registry().register(HomeSync.listener(cached));
        HomeRepository repository = HomeSync.repository(cached, bus.publisher());
        MainHomePreference mainHomes = HomeRepositories.mainHomePreference(persistence);
        HomeNotifier notifier = new HomeNotifier(kernel.messages(), kernel.messageSink());
        HomeQuota quota = new HomeQuota(kernel.permissions(), defaultLimit(ctx));
        HomeTeleporter teleporter = new TeleportHomeAdapter(teleportEngine);
        GuiLayout menuLayout = guiLayouts.load("homes", "homes-menu", GuiLayout.paginatedDefault(Material.RED_BED));
        HomeServices services = assemble(ctx, repository, mainHomes, notifier, quota, teleporter, menuLayout);
        return new Wired(HomeCommands.all(services, kernel.messages()), repository, quota);
    }

    private static HomeServices assemble(
            ModuleContext ctx,
            HomeRepository repository,
            MainHomePreference mainHomes,
            HomeNotifier notifier,
            HomeQuota quota,
            HomeTeleporter teleporter,
            GuiLayout menuLayout) {
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();
        TeleportHome teleportHome = new TeleportHome(repository, mainHomes, teleporter, notifier);
        HomeMenuView homeMenu = new HomeMenuView(kernel.messages(), kernel.scheduler(), teleportHome, menuLayout);
        return new HomeServices(
                new SetHome(repository, quota, notifier, kernel.events(), clock),
                new DelHome(repository, notifier, kernel.events()),
                new ListHomes(repository, notifier),
                teleportHome,
                new RenameHome(repository, notifier),
                new MoveHome(repository, notifier),
                new SetMainHome(repository, mainHomes, notifier),
                new HomeAdmin(repository, teleporter, notifier, kernel.events()),
                homeMenu,
                kernel.playerLookup());
    }

    private static int defaultLimit(ModuleContext ctx) {
        return Math.max(0, ctx.config().getInt("default-limit", DEFAULT_HOME_LIMIT));
    }

    /**
     * Everything the homes module contributes once wired: the Brigadier commands plus the read seams the
     * PlaceholderAPI expansion queries (the repository for the home count, the quota reducer for the limit).
     * The homes context holds no repeating scheduled work and no in-memory store beyond the repository
     * cache, so there is nothing to drain on stop — the module's {@code stop()} clears its own bookkeeping
     * and the cache expires.
     *
     * @param commands the Brigadier command registrations to publish
     * @param repository the home store the {@code homes_count} placeholder reads
     * @param quota the home-limit reducer the {@code homes_limit}/{@code homes_left} placeholders read
     */
    public record Wired(List<CommandRegistration> commands, HomeRepository repository, HomeQuota quota) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(quota, "quota");
        }
    }
}
