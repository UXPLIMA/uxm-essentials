package com.uxplima.uxmessentials.homes.adapter;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.InvitedPlayersMenu;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.InvitesMenuLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.listener.HomesJoinListener;
import com.uxplima.uxmessentials.homes.adapter.outbound.SafeLocationGuard;
import com.uxplima.uxmessentials.homes.adapter.outbound.TeleportHomeAdapter;
import com.uxplima.uxmessentials.homes.application.CreateHomeAtSlot;
import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.HomeAdmin;
import com.uxplima.uxmessentials.homes.application.HomeCharge;
import com.uxplima.uxmessentials.homes.application.HomeChargeSettings;
import com.uxplima.uxmessentials.homes.application.HomeNotifier;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.InviteToHome;
import com.uxplima.uxmessentials.homes.application.ListHomeInvites;
import com.uxplima.uxmessentials.homes.application.ListHomes;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.SetHomeIcon;
import com.uxplima.uxmessentials.homes.application.SetHomeVisibility;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.UninviteFromHome;
import com.uxplima.uxmessentials.homes.application.VisitHome;
import com.uxplima.uxmessentials.homes.application.WorldBlacklistGuard;
import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.application.port.SethomeGuard;
import com.uxplima.uxmessentials.homes.domain.HomeCost;
import com.uxplima.uxmessentials.persistence.homes.CachedHomeRepository;
import com.uxplima.uxmessentials.persistence.homes.HomeRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.HomeSync;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimProviders;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimServiceImpl;
import com.uxplima.uxmessentials.shared.application.claim.AlwaysAllowClaimService;
import com.uxplima.uxmessentials.shared.application.claim.ClaimPolicySettings;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaReduction;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the homes context's adapters, use cases, and slot-grid views over the injected kernel ports, the
 * persistence DSL, and the teleport context's engine, and produces the Brigadier command list the plugin
 * registers. This is the one place the homes context is wired — nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a Caffeine read-cache decorator (write-through at the delegate,
 * invalidate in the cache) wrapped by {@link HomeSync} so a local write announces itself across servers. The
 * teleporter delegates execution to the teleport context — homes never re-implements movement — which is why the
 * wiring receives the already-constructed {@link TeleportEngine}. Text prompts (home rename, invite add) go through
 * the shared {@link TextInput} seam installed once in bootstrap, so homes installs no input machinery of its own.
 */
@NullMarked
public final class HomesWiring {

    private static final int DEFAULT_HOME_LIMIT = 3;
    private static final int DEFAULT_UNLIMITED_MAX = 1000;
    private static final String DEFAULT_DATE_FORMAT = "dd/MM/yyyy HH:mm";
    private static final int DEFAULT_MIDAIR_GROUND_DEPTH = 5;

    private HomesWiring() {}

    /**
     * Build the homes adapters, use cases, and views from {@code ctx}, the persistence DSL, and the engine,
     * with no economy bridge (a configured home cost is recorded but not charged).
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            TeleportEngine teleportEngine,
            Bus bus,
            GuiLayouts guiLayouts,
            CloseableResources resources,
            TextInput textInput) {
        return wire(plugin, ctx, persistence, teleportEngine, Optional.empty(), bus, guiLayouts, resources, textInput);
    }

    /**
     * Build the homes context, charging a configured per-action cost through {@code homeEconomy} when
     * present. The economy context lands before homes in the registry, so its {@link HomeEconomy} bridge is
     * captured during economy wiring and handed in here; when it is empty (economy disabled or
     * {@code economy.enabled = false} in homes config), a configured cost is recorded but not charged.
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            TeleportEngine teleportEngine,
            Optional<HomeEconomy> homeEconomy,
            Bus bus,
            GuiLayouts guiLayouts,
            CloseableResources resources,
            TextInput textInput) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        Objects.requireNonNull(homeEconomy, "homeEconomy");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(textInput, "textInput");
        KernelPorts kernel = ctx.kernel();
        CachedHomeRepository cached = HomeRepositories.cachedConcrete(persistence);
        bus.registry().register(HomeSync.listener(cached));
        HomeRepository repository = HomeSync.repository(cached, bus.publisher());
        HomeInviteRepository invites = HomeRepositories.homeInviteRepository(persistence);
        HomeNotifier notifier = new HomeNotifier(kernel.messages(), kernel.messageSink());
        HomeQuota quota = new HomeQuota(kernel.permissions(), defaultLimit(ctx), limitMode(ctx));
        HomeTeleporter teleporter = new TeleportHomeAdapter(teleportEngine);
        HomeServices services = assemble(
                plugin, ctx, repository, invites, notifier, quota, teleporter, homeEconomy, guiLayouts, textInput);
        HomesJoinListener joinWarmer = new HomesJoinListener(repository, kernel.scheduler());
        return new Wired(
                HomeCommands.all(services, kernel.messages(), kernel.scheduler()),
                List.of(joinWarmer),
                repository,
                quota,
                services.homeList());
    }

    private static HomeServices assemble(
            Plugin plugin,
            ModuleContext ctx,
            HomeRepository repository,
            HomeInviteRepository invites,
            HomeNotifier notifier,
            HomeQuota quota,
            HomeTeleporter teleporter,
            Optional<HomeEconomy> homeEconomy,
            GuiLayouts guiLayouts,
            TextInput textInput) {
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();
        int unlimitedMax = unlimitedMax(ctx);
        DateTimeFormatter dateFormat = dateFormat(ctx);

        SafeLocationGuard safeGuard = buildSafeGuard(plugin, ctx);
        ClaimService claimService = buildClaimService(plugin, ctx, kernel);
        List<SethomeGuard> guards = buildGuards(ctx);
        HomeCharge charge = buildCharge(ctx, kernel, homeEconomy);
        CreateHomeAtSlot createHome = new CreateHomeAtSlot(
                repository, invites, quota, guards, notifier, kernel.events(), charge, unlimitedMax, clock);
        RelocateHome relocateHome = new RelocateHome(repository, guards, notifier, kernel.events(), charge, clock);
        RenameHome renameHome = new RenameHome(repository, notifier, kernel.events(), clock);
        SetHomeIcon setHomeIcon = new SetHomeIcon(repository, notifier, kernel.events(), clock);
        SetHomeVisibility setHomeVisibility = new SetHomeVisibility(repository, notifier, kernel.events(), clock);
        DeleteHome deleteHome = new DeleteHome(repository, invites, notifier, kernel.events());
        TeleportHome teleportHome = new TeleportHome(repository, teleporter, notifier, charge);
        ListHomes listHomes = new ListHomes(repository);
        ListHomeInvites listHomeInvites = new ListHomeInvites(invites);
        InviteToHome inviteToHome = new InviteToHome(repository, invites, notifier);
        UninviteFromHome uninviteFromHome = new UninviteFromHome(invites, notifier);
        VisitHome visitHome = new VisitHome(repository, invites, teleporter, notifier);

        boolean confirmDelete = ctx.config().getBoolean("confirm-delete", true);
        boolean confirmRelocate = ctx.config().getBoolean("confirm-relocate", false);
        boolean confirmUnsafeTeleport = ctx.config().getBoolean("confirm-unsafe-teleport", true);

        IconSelectorView iconSelector =
                new IconSelectorView(kernel.messages(), kernel.scheduler(), setHomeIcon, iconLayout(guiLayouts));
        InvitedPlayersMenu invitesMenu = new InvitedPlayersMenu(
                kernel.messages(),
                kernel.scheduler(),
                listHomeInvites,
                inviteToHome,
                uninviteFromHome,
                kernel.playerLookup(),
                notifier,
                textInput,
                invitesLayout(guiLayouts));
        HomeActionView actionView = new HomeActionView(
                kernel.messages(),
                notifier,
                kernel.permissions(),
                kernel.scheduler(),
                teleportHome,
                deleteHome,
                relocateHome,
                renameHome,
                setHomeVisibility,
                iconSelector,
                invitesMenu,
                repository,
                textInput,
                actionsLayout(guiLayouts),
                dateFormat,
                confirmDelete,
                confirmRelocate,
                confirmUnsafeTeleport,
                safeGuard.blockUnsafe(),
                (Position pos) -> safeGuard.isUnsafe(pos),
                claimService);
        HomeListView listView = new HomeListView(
                kernel.messages(),
                notifier,
                kernel.permissions(),
                kernel.scheduler(),
                listHomes,
                quota,
                createHome,
                safeGuard,
                claimService,
                actionView,
                listLayout(guiLayouts),
                unlimitedMax,
                dateFormat);
        HomeAdmin homeAdmin = new HomeAdmin(repository, invites, teleporter, notifier, kernel.events(), clock);
        return new HomeServices(
                listView,
                actionView,
                iconSelector,
                homeAdmin,
                visitHome,
                inviteToHome,
                uninviteFromHome,
                kernel.playerLookup(),
                repository);
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

    private static InvitesMenuLayout invitesLayout(GuiLayouts guiLayouts) {
        return guiLayouts.loadInvitesMenu("homes", "invites-menu", InvitesMenuLayout.codeDefault());
    }

    private static int defaultLimit(ModuleContext ctx) {
        return Math.max(0, ctx.config().getInt("default-limit", DEFAULT_HOME_LIMIT));
    }

    private static QuotaReduction limitMode(ModuleContext ctx) {
        String raw = ctx.config().getString("limit-mode", "highest");
        return "stack".equalsIgnoreCase(raw) ? QuotaReduction.STACK : QuotaReduction.MAX;
    }

    private static int unlimitedMax(ModuleContext ctx) {
        return Math.max(1, ctx.config().getInt("unlimited-max", DEFAULT_UNLIMITED_MAX));
    }

    private static DateTimeFormatter dateFormat(ModuleContext ctx) {
        String pattern = ctx.config().getString("date-format", DEFAULT_DATE_FORMAT);
        return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
    }

    private static ClaimService buildClaimService(Plugin plugin, ModuleContext ctx, KernelPorts kernel) {
        boolean enabled = ctx.config().getBoolean("claims.enabled", true);
        if (!enabled) {
            return new AlwaysAllowClaimService();
        }
        boolean requireClaim = ctx.config().getBoolean("claims.require-claim", false);
        boolean blockForeignClaims = ctx.config().getBoolean("claims.block-foreign-claims", true);
        int foreignChunkDistance = Math.max(0, ctx.config().getInt("claims.foreign-claim-chunk-distance", 0));
        boolean checkTeleportAccess = ctx.config().getBoolean("claims.check-teleport-access", true);
        ClaimPolicySettings settings =
                new ClaimPolicySettings(requireClaim, blockForeignClaims, foreignChunkDistance, checkTeleportAccess);
        return new ClaimServiceImpl(ClaimProviders.detect(plugin, plugin.getServer(), kernel.log()), settings);
    }

    private static SafeLocationGuard buildSafeGuard(Plugin plugin, ModuleContext ctx) {
        boolean blockUnsafe = ctx.config().getBoolean("block-unsafe-sethome", true);
        boolean considerMidair = ctx.config().getBoolean("consider-midair-unsafe", true);
        int midairDepth = Math.max(1, ctx.config().getInt("midair-ground-depth", DEFAULT_MIDAIR_GROUND_DEPTH));
        return new SafeLocationGuard(plugin.getServer(), blockUnsafe, considerMidair, midairDepth);
    }

    private static List<SethomeGuard> buildGuards(ModuleContext ctx) {
        // Only the pure, Bukkit-free guard runs inside the use cases — they execute async, where a block
        // read is illegal. The block-reading SafeLocationGuard is invoked by the views on the region thread.
        Set<String> disabledWorlds = new HashSet<>(ctx.config().getStringList("disabled-worlds", List.of()));
        return List.of(new WorldBlacklistGuard(disabledWorlds));
    }

    private static HomeCharge buildCharge(ModuleContext ctx, KernelPorts kernel, Optional<HomeEconomy> homeEconomy) {
        boolean economyEnabled = ctx.config().getBoolean("economy.enabled", false);
        if (!economyEnabled || homeEconomy.isEmpty()) {
            // Economy disabled in config or no provider wired — all actions are free.
            return new HomeCharge(kernel.permissions(), Optional.empty(), HomeChargeSettings.allFree());
        }
        String currency = ctx.config().getString("economy.currency", "default");
        HomeCost createCost = toCost(ctx.config().getDouble("economy.create-cost", 0), currency);
        HomeCost relocateCost = toCost(ctx.config().getDouble("economy.relocate-cost", 0), currency);
        HomeCost teleportCost = toCost(ctx.config().getDouble("economy.teleport-cost", 0), currency);
        HomeChargeSettings settings = new HomeChargeSettings(createCost, relocateCost, teleportCost);
        return new HomeCharge(kernel.permissions(), homeEconomy, settings);
    }

    private static HomeCost toCost(double raw, String currency) {
        BigDecimal amount = BigDecimal.valueOf(raw);
        return amount.signum() > 0 ? HomeCost.of(amount, currency) : HomeCost.free();
    }

    /**
     * Everything the homes module contributes once wired: the Brigadier commands plus the read seams the
     * PlaceholderAPI expansion queries. The homes context holds no repeating scheduled work and installs no
     * input machinery of its own (text prompts go through the shared seam), so there is nothing to drain on stop.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join cache-warmer the plugin registers
     * @param repository the home store the {@code homes_count} placeholder reads
     * @param quota the home-limit reducer the {@code homes_limit}/{@code homes_left} placeholders read
     * @param listView the slot-grid home list the {@code /home} command and the management hub both open
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            HomeRepository repository,
            HomeQuota quota,
            HomeListView listView) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(quota, "quota");
            Objects.requireNonNull(listView, "listView");
        }
    }
}
