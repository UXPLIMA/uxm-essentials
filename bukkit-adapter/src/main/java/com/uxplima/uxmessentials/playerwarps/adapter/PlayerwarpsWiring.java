package com.uxplima.uxmessentials.playerwarps.adapter;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.persistence.playerwarps.JooqPlayerWarpEconomy;
import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpDataMigration;
import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpRenameNotice;
import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.command.PlayerWarpCommands;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpEditorSubLayouts;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpEditorView;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpListMenu;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.listener.PlayerwarpsJoinListener;
import com.uxplima.uxmessentials.playerwarps.adapter.outbound.TeleportPlayerWarpAdapter;
import com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.ListPlayerWarps;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpNotifier;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.WarpAuthorization;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the player-warps context's adapters and use cases over the injected kernel ports, the persistence
 * DSL, and the teleport context's engine, and produces the Brigadier command list the plugin registers. This
 * is the one place the player-warps context is wired — nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a Caffeine read-cache decorator keyed by owner (write-through
 * at the delegate, invalidate in the cache). The teleporter delegates execution to the teleport context —
 * player-warps never re-implements movement — which is why the wiring receives the already-constructed
 * {@link TeleportEngine}. The per-owner count limit resolves through {@link PlayerWarpQuota} over the shared
 * {@code Permissions} reducer with the module's {@code default-limit} config value as the fallback.
 */
@NullMarked
public final class PlayerwarpsWiring {

    private static final int DEFAULT_LIMIT = 3;

    private PlayerwarpsWiring() {}

    /**
     * Build the player-warps adapters and use cases over the kernel ports and the teleport engine. The warp
     * arrival-notification registry is shared from the warps module so a player-warp hop fires the same welcome
     * effects; when warps is disabled it is {@code null} and player-warps falls back to a private throwaway
     * registry (no listener consumes it, exactly as before this was injected).
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            TeleportEngine teleportEngine,
            com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus bus,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable WarpEditorView
                    editorView,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable PlayerWarpRepositoryHandle
                    playerWarpHandle,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable PlayerWarpGoToHandle
                    playerWarpGoTo,
            com.uxplima.uxmessentials.warps.adapter.@org.jspecify.annotations.Nullable WarpTeleportRegistry
                    teleportRegistry,
            GuiText guiText,
            GuiLayouts guiLayouts,
            TextInput textInput,
            ManagementGuiRegistry guiRegistry,
            Menus menus,
            MenuBindings menuBindings,
            @org.jspecify.annotations.Nullable EconomyProvider economyProvider,
            @org.jspecify.annotations.Nullable CurrencyRegistry economyCurrencies,
            @org.jspecify.annotations.Nullable CurrencyBackendRegistry economyBackends) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(guiRegistry, "guiRegistry");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(menuBindings, "menuBindings");
        KernelPorts kernel = ctx.kernel();
        runLegacyMigration(plugin, persistence, kernel, bus.publisher().serverId());
        // The concrete cache is what the cross-server listener invalidates per owner; the broadcasting decorator
        // wraps that same cache so a local write announces it to peers (the homes seam, copied for player-warps).
        com.uxplima.uxmessentials.persistence.playerwarps.CachedPlayerWarpRepository cached =
                PlayerWarpRepositories.cachedConcrete(
                        persistence,
                        com.uxplima.uxmessentials.shared.adapter.outbound.lookup.PlayerNames.resolver(
                                kernel.playerLookup()));
        bus.registry().register(com.uxplima.uxmessentials.shared.adapter.outbound.bus.PlayerWarpSync.listener(cached));
        PlayerWarpRepository repository =
                com.uxplima.uxmessentials.shared.adapter.outbound.bus.PlayerWarpSync.repository(
                        cached, bus.publisher());
        PlayerWarpNotifier notifier = new PlayerWarpNotifier(kernel.messages(), kernel.messageSink());
        com.uxplima.uxmessentials.warps.adapter.WarpTeleportRegistry registry = teleportRegistry != null
                ? teleportRegistry
                : new com.uxplima.uxmessentials.warps.adapter.WarpTeleportRegistry();
        PlayerWarpTeleporter teleporter = new TeleportPlayerWarpAdapter(teleportEngine, registry);
        PlayerWarpQuota quota = new PlayerWarpQuota(kernel.permissions(), defaultLimit(ctx));
        // UsePlayerWarp is built once so the /pwarp command, the browse menu, and the shared warp editor's "go to"
        // button all teleport through the same path; the go-to handle the warps editor reads is bound to it here.
        // The ordered access gate reads the ban/member/whitelist/password stores and rate-limits password attempts
        // through the shared Cooldowns port. The economy seam is the jOOQ bridge over the resolved provider — a
        // priced warp charges the visitor and banks the owner's cut through it; when economy is disabled it stays
        // empty and a priced warp teleports for free (the WarpEconomy soft-coupling precedent).
        Optional<PlayerWarpEconomy> economy =
                playerWarpEconomy(ctx, persistence, kernel, economyProvider, economyCurrencies, economyBackends);
        // The member store backs both the ordered access gate (a member reaches a private warp) and the management
        // authorization (a member's role decides which edit verbs they may run), so it is built once and shared.
        WarpMemberStore memberStore = PlayerWarpRepositories.memberStore(persistence, kernel.log());
        WarpAuthorization warpAuthorization = new WarpAuthorization(memberStore);
        UsePlayerWarp usePlayerWarp = new UsePlayerWarp(
                repository,
                teleporter,
                notifier,
                new com.uxplima.uxmessentials.warps.adapter.outbound.BukkitWarpSafetyChecker(),
                kernel.permissions(),
                PlayerWarpRepositories.banStore(persistence),
                memberStore,
                PlayerWarpRepositories.whitelistStore(persistence, Clock.systemUTC()),
                PlayerWarpRepositories.passwordStore(persistence),
                kernel.cooldowns(),
                economy,
                Clock.systemUTC());
        if (playerWarpHandle != null) {
            playerWarpHandle.bind(repository);
        }
        if (playerWarpGoTo != null) {
            playerWarpGoTo.bind((viewer, owner, name) -> usePlayerWarp.useFor(
                    viewer, owner, com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName.of(name)));
        }
        // Build the use cases, then the management GUI over them, then the services holder carrying both editors.
        SetPlayerWarp setPlayerWarp = new SetPlayerWarp(
                repository,
                quota,
                notifier,
                kernel.events(),
                Clock.systemUTC(),
                ctx.config().getStringList("world-blacklist", List.of()));
        ArchivePlayerWarp archivePlayerWarp =
                new ArchivePlayerWarp(repository, warpAuthorization, notifier, kernel.events(), Clock.systemUTC());
        SetPlayerWarpVisibility visibility = new SetPlayerWarpVisibility(repository, notifier, Clock.systemUTC());
        PlayerWarpListMenu listMenu = buildGui(
                plugin,
                kernel,
                repository,
                setPlayerWarp,
                visibility,
                archivePlayerWarp,
                guiText,
                guiLayouts,
                textInput,
                menus);
        listMenu.register(menuBindings, plugin.getDataFolder().toPath(), kernel.log());
        guiRegistry.register(new ManagementGuiEntry(
                "playerwarps",
                PlayerwarpsMessageKey.PWARP_GUI_LIST_TITLE,
                org.bukkit.Material.ENDER_PEARL,
                PlayerWarpListMenu.MANAGE_PERMISSION,
                listMenu::open));
        PlayerWarpServices services = assemble(
                kernel,
                repository,
                usePlayerWarp,
                setPlayerWarp,
                archivePlayerWarp,
                visibility,
                notifier,
                editorView,
                listMenu);
        PlayerwarpsJoinListener joinWarmer = new PlayerwarpsJoinListener(repository, kernel.scheduler());
        return new Wired(PlayerWarpCommands.all(services, kernel.messages()), List.of(joinWarmer), repository, quota);
    }

    /**
     * Build the jOOQ economy bridge over the resolved provider, or {@link Optional#empty()} when economy is
     * disabled (any of the provider or the two currency registries absent) so a priced warp stays free — the
     * {@code WarpEconomy} soft-coupling precedent. The owner-cut percentage and the auto-payout switch are read
     * from this module's own {@code payout} config block, keeping the seam as narrow as the gate needs.
     */
    private static Optional<PlayerWarpEconomy> playerWarpEconomy(
            ModuleContext ctx,
            Persistence persistence,
            KernelPorts kernel,
            @org.jspecify.annotations.Nullable EconomyProvider economyProvider,
            @org.jspecify.annotations.Nullable CurrencyRegistry economyCurrencies,
            @org.jspecify.annotations.Nullable CurrencyBackendRegistry economyBackends) {
        if (economyProvider == null || economyCurrencies == null || economyBackends == null) {
            return Optional.empty();
        }
        double rawCutPercent = ctx.config().getDouble("payout.cut-percent", 10.0);
        if (rawCutPercent < 0.0 || rawCutPercent > 100.0) {
            // PayoutConfig clamps this to [0, 100] so a typo can never bank a negative or over-price cut; warn once
            // here, at the read site, so the operator sees their configured value was out of range and corrected.
            kernel.log().warn("event=playerwarp_cut_percent_clamped configured={} clamped_to=[0,100]", rawCutPercent);
        }
        BigDecimal cutPercent = BigDecimal.valueOf(rawCutPercent);
        boolean autoPayout = ctx.config().getBoolean("payout.auto-payout", false);
        return Optional.of(new JooqPlayerWarpEconomy(
                persistence,
                economyProvider,
                economyCurrencies,
                economyBackends,
                new JooqPlayerWarpEconomy.PayoutConfig(cutPercent, autoPayout),
                kernel.log()));
    }

    /**
     * Copy the shipped player-warp data from the V70 {@code _v1_legacy} tables into the new surrogate-id schema,
     * once, off the tick thread, then claim this backend's {@code server_id} onto the NULL-{@code server_id} rows
     * (the migrated ones and any left by the basic create path) in the same pass. The routine self-disables by
     * dropping the legacy tables after a successful copy, so scheduling it on every enable is safe: after the first
     * run there is nothing left to migrate and it returns immediately. It stays off the tick thread because the whole
     * scan and its writes must never block enable.
     *
     * <p>A now-duplicate name is renamed on the way in, and the owner is told through a callback so the migration
     * itself need not know about the messaging module. Reaching the offline-mailbox from here would mean threading
     * the messaging context's send-mail port through this wiring, which it does not yet carry, so a rename is
     * recorded in the operator log for now; delivering it as mail to the owner is a follow-up.
     *
     * <p>The world list is read here, on the enable thread, because {@code Server#getWorlds()} is a main-thread read;
     * the immutable uid snapshot is then handed to the off-tick task. A world loaded after enable stays NULL until
     * the next enable's claim, which is acceptable — the cross-server phase only needs a stable home tag, and P4's
     * rich-create can stamp a warp on create.
     */
    private static void runLegacyMigration(
            Plugin plugin, Persistence persistence, KernelPorts kernel, String serverId) {
        Consumer<PlayerWarpRenameNotice> onRename = notice -> kernel.log()
                .info(
                        "event=playerwarp_renamed owner={} from={} to={}",
                        notice.owner(),
                        notice.oldName(),
                        notice.newName());
        List<String> localWorldUids = plugin.getServer().getWorlds().stream()
                .map(world -> world.getUID().toString())
                .toList();
        kernel.scheduler().async(() -> {
            PlayerWarpDataMigration.run(persistence, onRename, kernel.log());
            claimServerId(persistence, kernel, serverId, localWorldUids);
        });
    }

    /**
     * Stamp this backend's {@code network.server-id} onto the freshly-migrated (and any other) NULL-{@code server_id}
     * rows living in this backend's worlds, logging the affected-row count. A blank server-id is a misconfiguration
     * that must not crash enable, so the claim is skipped with a debug line and the rows are left for the next pass.
     */
    private static void claimServerId(
            Persistence persistence, KernelPorts kernel, String serverId, List<String> localWorldUids) {
        if (serverId.isBlank()) {
            kernel.log().debug("event=playerwarp_server_claim_skipped reason=blank_server_id");
            return;
        }
        int claimed = com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpServerClaimer.claim(
                persistence, serverId, localWorldUids);
        kernel.log().info("event=playerwarp_server_claim server={} claimed={}", serverId, claimed);
    }

    /**
     * Build the player-warp management list (now an engine menu) and editor over the shared GUI framework. The
     * editor's back button reopens the list, so a one-slot holder breaks the list↔editor construction cycle (the
     * editor is built first, the list second, and the holder is filled before either is shown) — the same pattern
     * the NPC GUI uses. The list's grid geometry and per-entry rendering now live in the {@code playerwarp-list}
     * menu spec rather than the old {@code pwarp-list.conf} layout, so only the editor still reads a GUI layout.
     */
    private static PlayerWarpListMenu buildGui(
            Plugin plugin,
            KernelPorts kernel,
            PlayerWarpRepository repository,
            SetPlayerWarp setPlayerWarp,
            SetPlayerWarpVisibility visibility,
            ArchivePlayerWarp archivePlayerWarp,
            GuiText guiText,
            GuiLayouts guiLayouts,
            TextInput textInput,
            Menus menus) {
        PlayerWarpEditorSubLayouts subLayouts = PlayerWarpEditorSubLayouts.load(
                plugin.getDataFolder().toPath(), "playerwarps", "pwarp-editor", kernel.log());
        EntityEditorLayout editorLayout =
                guiLayouts.loadEntityEditor("playerwarps", "pwarp-editor", editorCodeDefault());
        PlayerWarpListMenu[] listHolder = new PlayerWarpListMenu[1];
        PlayerWarpEditorView editor = new PlayerWarpEditorView(
                menus,
                guiText,
                kernel.scheduler(),
                repository,
                visibility,
                archivePlayerWarp,
                textInput,
                kernel.messages(),
                editorLayout,
                subLayouts,
                (player, viewer) -> listHolder[0].open(player, viewer));
        PlayerWarpListMenu listMenu = new PlayerWarpListMenu(
                menus,
                kernel.scheduler(),
                kernel.permissions(),
                kernel.messages(),
                repository,
                setPlayerWarp,
                textInput,
                editor);
        listHolder[0] = listMenu;
        return listMenu;
    }

    /** The editor's property-button slots, the code default matching the bundled pwarp-editor.conf. */
    private static final List<Integer> EDITOR_PROPERTY_SLOTS = List.of(10, 11, 12, 13, 14, 15, 19, 20, 21, 22);

    /**
     * The 6-row editor code default used when no {@code pwarp-editor.conf} is present. The shared
     * {@link EntityEditorLayout#withDelete} factory is a 3-row default that cannot hold the property slots, so this
     * builds the layout directly with the bundled geometry (the same fix the holograms/NPC editors apply).
     */
    private static EntityEditorLayout editorCodeDefault() {
        return new EntityEditorLayout(
                6,
                EDITOR_PROPERTY_SLOTS,
                49,
                java.util.OptionalInt.of(53),
                org.bukkit.Material.ARROW,
                org.bukkit.Material.BARRIER,
                org.bukkit.Material.BLACK_STAINED_GLASS_PANE);
    }

    private static PlayerWarpServices assemble(
            KernelPorts kernel,
            PlayerWarpRepository repository,
            UsePlayerWarp usePlayerWarp,
            SetPlayerWarp setPlayerWarp,
            ArchivePlayerWarp archivePlayerWarp,
            SetPlayerWarpVisibility visibility,
            PlayerWarpNotifier notifier,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable WarpEditorView
                    editorView,
            PlayerWarpListMenu listMenu) {
        return new PlayerWarpServices(
                setPlayerWarp,
                archivePlayerWarp,
                usePlayerWarp,
                new ListPlayerWarps(repository, notifier),
                visibility,
                kernel.playerLookup(),
                repository,
                editorView,
                kernel.scheduler(),
                listMenu);
    }

    private static int defaultLimit(ModuleContext ctx) {
        return Math.max(0, ctx.config().getInt("default-limit", DEFAULT_LIMIT));
    }

    /**
     * Everything the player-warps module contributes once wired: the Brigadier commands, the join cache-warmer,
     * and the read ports the PAPI seam queries. The context holds no repeating scheduled work and no in-memory
     * store beyond the repository cache, so there is nothing to drain on stop — the module's {@code stop()}
     * clears its own bookkeeping and the cache expires.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join cache-warmer the plugin registers
     * @param repository the cached player-warp repository the PAPI seam reads owned warps from
     * @param quota the per-owner count-limit reducer the PAPI seam reads the limit through
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            PlayerWarpRepository repository,
            PlayerWarpQuota quota) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(quota, "quota");
        }
    }
}
