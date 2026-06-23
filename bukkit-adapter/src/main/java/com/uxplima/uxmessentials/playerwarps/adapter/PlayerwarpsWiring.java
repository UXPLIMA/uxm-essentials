package com.uxplima.uxmessentials.playerwarps.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.command.PlayerWarpCommands;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpEditorSubLayouts;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpEditorView;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpListView;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.listener.PlayerwarpsJoinListener;
import com.uxplima.uxmessentials.playerwarps.adapter.outbound.TeleportPlayerWarpAdapter;
import com.uxplima.uxmessentials.playerwarps.application.DelPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.ListPlayerWarps;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpNotifier;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
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
            ManagementGuiRegistry guiRegistry) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(teleportEngine, "teleportEngine");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(guiRegistry, "guiRegistry");
        KernelPorts kernel = ctx.kernel();
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
        UsePlayerWarp usePlayerWarp = new UsePlayerWarp(
                repository,
                teleporter,
                notifier,
                new com.uxplima.uxmessentials.warps.adapter.outbound.BukkitWarpSafetyChecker(),
                kernel.permissions());
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
        DelPlayerWarp delPlayerWarp = new DelPlayerWarp(repository, notifier, kernel.events());
        SetPlayerWarpVisibility visibility = new SetPlayerWarpVisibility(repository, notifier);
        PlayerWarpListView listView = buildGui(
                plugin, kernel, repository, setPlayerWarp, visibility, delPlayerWarp, guiText, guiLayouts, textInput);
        guiRegistry.register(new ManagementGuiEntry(
                "playerwarps",
                PlayerwarpsMessageKey.PWARP_GUI_LIST_TITLE,
                org.bukkit.Material.ENDER_PEARL,
                PlayerWarpListView.MANAGE_PERMISSION,
                listView::open));
        PlayerWarpServices services = assemble(
                kernel,
                repository,
                usePlayerWarp,
                setPlayerWarp,
                delPlayerWarp,
                visibility,
                notifier,
                editorView,
                listView);
        PlayerwarpsJoinListener joinWarmer = new PlayerwarpsJoinListener(repository, kernel.scheduler());
        return new Wired(PlayerWarpCommands.all(services, kernel.messages()), List.of(joinWarmer), repository, quota);
    }

    /**
     * Build the player-warp management list and editor over the shared GUI framework. The editor's back button
     * reopens the list, so a one-slot holder breaks the list↔editor construction cycle (the editor is built first,
     * the list second, and the holder is filled before either is shown) — the same pattern the NPC GUI uses.
     */
    private static PlayerWarpListView buildGui(
            Plugin plugin,
            KernelPorts kernel,
            PlayerWarpRepository repository,
            SetPlayerWarp setPlayerWarp,
            SetPlayerWarpVisibility visibility,
            DelPlayerWarp delPlayerWarp,
            GuiText guiText,
            GuiLayouts guiLayouts,
            TextInput textInput) {
        PlayerWarpEditorSubLayouts subLayouts = PlayerWarpEditorSubLayouts.load(
                plugin.getDataFolder().toPath(), "playerwarps", "pwarp-editor", kernel.log());
        EntityListLayout listLayout = guiLayouts.loadEntityList(
                "playerwarps",
                "pwarp-list",
                EntityListLayout.withCreate(org.bukkit.Material.ENDER_PEARL, 49, org.bukkit.Material.LIME_DYE));
        EntityEditorLayout editorLayout =
                guiLayouts.loadEntityEditor("playerwarps", "pwarp-editor", editorCodeDefault());
        PlayerWarpListView[] listHolder = new PlayerWarpListView[1];
        PlayerWarpEditorView editor = new PlayerWarpEditorView(
                guiText,
                kernel.scheduler(),
                repository,
                visibility,
                delPlayerWarp,
                textInput,
                kernel.messages(),
                editorLayout,
                subLayouts,
                (player, viewer) -> listHolder[0].open(player, viewer));
        PlayerWarpListView listView = new PlayerWarpListView(
                guiText,
                kernel.scheduler(),
                kernel.permissions(),
                kernel.messages(),
                repository,
                setPlayerWarp,
                textInput,
                listLayout,
                editor);
        listHolder[0] = listView;
        return listView;
    }

    /** The editor's property-button slots, the code default matching the bundled pwarp-editor.conf. */
    private static final List<Integer> EDITOR_PROPERTY_SLOTS = List.of(10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24);

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
            DelPlayerWarp delPlayerWarp,
            SetPlayerWarpVisibility visibility,
            PlayerWarpNotifier notifier,
            com.uxplima.uxmessentials.warps.adapter.inbound.gui.@org.jspecify.annotations.Nullable WarpEditorView
                    editorView,
            PlayerWarpListView listView) {
        return new PlayerWarpServices(
                setPlayerWarp,
                delPlayerWarp,
                usePlayerWarp,
                new ListPlayerWarps(repository, notifier),
                visibility,
                kernel.playerLookup(),
                repository,
                editorView,
                kernel.scheduler(),
                listView);
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
