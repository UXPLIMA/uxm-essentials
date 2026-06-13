package com.uxplima.uxmessentials.homes.adapter;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.bootstrap.di.CloseableResources;
import com.uxplima.uxmessentials.homes.adapter.inbound.command.HomeCommands;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeActionView;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeActionsLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListView;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.IconSelectorLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.IconSelectorView;
import com.uxplima.uxmessentials.homes.adapter.inbound.listener.HomesJoinListener;
import com.uxplima.uxmessentials.homes.adapter.outbound.TeleportHomeAdapter;
import com.uxplima.uxmessentials.homes.application.CreateHomeAtSlot;
import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.HomeAdmin;
import com.uxplima.uxmessentials.homes.application.HomeNotifier;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.ListHomes;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.SetHomeIcon;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.persistence.homes.CachedHomeRepository;
import com.uxplima.uxmessentials.persistence.homes.HomeRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.HomeSync;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the homes context's adapters, use cases, and slot-grid views over the injected kernel ports, the
 * persistence DSL, and the teleport context's engine, and produces the Brigadier command list the plugin
 * registers. This is the one place the homes context is wired — nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a Caffeine read-cache decorator (write-through at the delegate,
 * invalidate in the cache) wrapped by {@link HomeSync} so a local write announces itself across servers. The
 * teleporter delegates execution to the teleport context — homes never re-implements movement — which is why the
 * wiring receives the already-constructed {@link TeleportEngine}. The anvil-input helper used for renaming is
 * installed once here and torn down on disable.
 */
@NullMarked
public final class HomesWiring {

    private static final int DEFAULT_HOME_LIMIT = 3;
    private static final int DEFAULT_UNLIMITED_MAX = 1000;
    private static final String DEFAULT_DATE_FORMAT = "dd/MM/yyyy HH:mm";

    private HomesWiring() {}

    /** Build the homes adapters, use cases, and views from {@code ctx}, the persistence DSL, and the engine. */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            TeleportEngine teleportEngine,
            Bus bus,
            GuiLayouts guiLayouts,
            CloseableResources resources) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(resources, "resources");
        KernelPorts kernel = ctx.kernel();
        CachedHomeRepository cached = HomeRepositories.cachedConcrete(persistence);
        bus.registry().register(HomeSync.listener(cached));
        HomeRepository repository = HomeSync.repository(cached, bus.publisher());
        HomeNotifier notifier = new HomeNotifier(kernel.messages(), kernel.messageSink());
        HomeQuota quota = new HomeQuota(kernel.permissions(), defaultLimit(ctx));
        HomeTeleporter teleporter = new TeleportHomeAdapter(teleportEngine);
        HomeServices services = assemble(plugin, ctx, repository, notifier, quota, teleporter, guiLayouts, resources);
        HomesJoinListener joinWarmer = new HomesJoinListener(repository, kernel.scheduler());
        return new Wired(HomeCommands.all(services, kernel.messages()), List.of(joinWarmer), repository, quota);
    }

    private static HomeServices assemble(
            Plugin plugin,
            ModuleContext ctx,
            HomeRepository repository,
            HomeNotifier notifier,
            HomeQuota quota,
            HomeTeleporter teleporter,
            GuiLayouts guiLayouts,
            CloseableResources resources) {
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();
        int unlimitedMax = unlimitedMax(ctx);
        DateTimeFormatter dateFormat = dateFormat(ctx);
        AnvilInput anvil = installAnvil(plugin, resources);

        CreateHomeAtSlot createHome =
                new CreateHomeAtSlot(repository, quota, List.of(), notifier, kernel.events(), unlimitedMax, clock);
        RelocateHome relocateHome = new RelocateHome(repository, notifier, kernel.events(), clock);
        RenameHome renameHome = new RenameHome(repository, notifier, kernel.events(), clock);
        SetHomeIcon setHomeIcon = new SetHomeIcon(repository, notifier, kernel.events(), clock);
        DeleteHome deleteHome = new DeleteHome(repository, notifier, kernel.events());
        TeleportHome teleportHome = new TeleportHome(repository, teleporter, notifier);
        ListHomes listHomes = new ListHomes(repository);

        IconSelectorView iconSelector =
                new IconSelectorView(kernel.messages(), kernel.scheduler(), setHomeIcon, iconLayout(guiLayouts));
        HomeActionView actionView = new HomeActionView(
                kernel.messages(),
                notifier,
                kernel.permissions(),
                kernel.scheduler(),
                teleportHome,
                deleteHome,
                relocateHome,
                renameHome,
                iconSelector,
                anvil,
                actionsLayout(guiLayouts),
                dateFormat);
        HomeListView listView = new HomeListView(
                kernel.messages(),
                kernel.scheduler(),
                listHomes,
                quota,
                createHome,
                actionView,
                listLayout(guiLayouts),
                unlimitedMax,
                dateFormat);
        HomeAdmin homeAdmin = new HomeAdmin(repository, teleporter, notifier, kernel.events());
        return new HomeServices(listView, actionView, iconSelector, homeAdmin, kernel.playerLookup(), repository);
    }

    private static AnvilInput installAnvil(Plugin plugin, CloseableResources resources) {
        AnvilInput anvil = new AnvilInput(plugin);
        anvil.install();
        resources.onClose(anvil::uninstall);
        return anvil;
    }

    private static HomeListLayout listLayout(GuiLayouts guiLayouts) {
        return guiLayouts.loadHomeList("homes", "home-list", HomeListLayout.codeDefault());
    }

    private static HomeActionsLayout actionsLayout(GuiLayouts guiLayouts) {
        return guiLayouts.loadHomeActions("homes", "home-actions", HomeActionsLayout.codeDefault());
    }

    private static IconSelectorLayout iconLayout(GuiLayouts guiLayouts) {
        return guiLayouts.loadIconSelector("homes", "icon-selector", IconSelectorLayout.codeDefault());
    }

    private static int defaultLimit(ModuleContext ctx) {
        return Math.max(0, ctx.config().getInt("default-limit", DEFAULT_HOME_LIMIT));
    }

    private static int unlimitedMax(ModuleContext ctx) {
        return Math.max(1, ctx.config().getInt("unlimited-max", DEFAULT_UNLIMITED_MAX));
    }

    private static DateTimeFormatter dateFormat(ModuleContext ctx) {
        String pattern = ctx.config().getString("date-format", DEFAULT_DATE_FORMAT);
        return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
    }

    /**
     * Everything the homes module contributes once wired: the Brigadier commands plus the read seams the
     * PlaceholderAPI expansion queries. The homes context holds no repeating scheduled work, so there is
     * nothing to drain on stop beyond the anvil/navigator teardown already registered with the resources.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join cache-warmer the plugin registers
     * @param repository the home store the {@code homes_count} placeholder reads
     * @param quota the home-limit reducer the {@code homes_limit}/{@code homes_left} placeholders read
     */
    public record Wired(
            List<CommandRegistration> commands, List<Listener> listeners, HomeRepository repository, HomeQuota quota) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(quota, "quota");
        }
    }
}
