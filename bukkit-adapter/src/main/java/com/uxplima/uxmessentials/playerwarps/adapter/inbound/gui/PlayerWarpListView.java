package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /pwarp} management list: a config-driven, paginated grid of player-owned warps drawn through the
 * shared {@link EntityListView}, each icon opening the {@link PlayerWarpEditorView} on click. The scope is
 * resolved per open against the viewer's permission — a plain player sees only their own warps
 * ({@code repository.ownedBy}); an operator holding {@code uxmessentials.pwarp.gui} sees every player's
 * ({@code repository.all}) so they can manage all. A create button opens an anvil for a new name and creates a
 * private warp at the operator's feet through the existing {@link SetPlayerWarp} use case (the same one
 * {@code /setpwarp} calls).
 *
 * <p>Because the scope and the per-warp owner depend on who is opening, the view is built fresh per open (the
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementHubView} pattern): the entity supplier
 * closes over the opening viewer, so a player who gains the manage node mid-session sees every warp the next
 * time they open. An owner-scoped read is cheap; the cross-owner admin scan is bounded and only runs on a
 * deliberate operator open.
 */
@NullMarked
public final class PlayerWarpListView {

    /** The node an operator holds to manage every player's warps (and to open this list as an admin). */
    public static final String MANAGE_PERMISSION = "uxmessentials.pwarp.gui";

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Permissions permissions;
    private final Messages messages;
    private final PlayerWarpRepository repository;
    private final SetPlayerWarp setPlayerWarp;
    private final AnvilInput anvil;
    private final EntityListLayout layout;
    private final PlayerWarpEditorView editor;

    public PlayerWarpListView(
            GuiText guiText,
            Scheduler scheduler,
            Permissions permissions,
            Messages messages,
            PlayerWarpRepository repository,
            SetPlayerWarp setPlayerWarp,
            AnvilInput anvil,
            EntityListLayout layout,
            PlayerWarpEditorView editor) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.setPlayerWarp = Objects.requireNonNull(setPlayerWarp, "setPlayerWarp");
        this.anvil = Objects.requireNonNull(anvil, "anvil");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.editor = Objects.requireNonNull(editor, "editor");
    }

    /** Open the warp list for {@code viewer} on their entity thread, scoped to what they may manage. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        view(viewer).open(player, viewer);
    }

    private EntityListView<OwnedWarp> view(PlayerRef viewer) {
        return EntityListView.<OwnedWarp>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(PlayerwarpsMessageKey.PWARP_GUI_LIST_TITLE)
                .navNames(PlayerwarpsMessageKey.PWARP_GUI_LIST_PREV, PlayerwarpsMessageKey.PWARP_GUI_LIST_NEXT)
                .entities(() -> entities(viewer))
                .iconRenderer(this::icon)
                .onSelect((player, owned) -> editor.open(player, viewer, owned))
                .onCreate(PlayerwarpsMessageKey.PWARP_GUI_LIST_CREATE, player -> promptCreate(player, viewer))
                .build();
    }

    /** Every warp the viewer may manage: all players' when they hold the manage node, else only their own. */
    private List<OwnedWarp> entities(PlayerRef viewer) {
        List<PlayerWarp> warps =
                permissions.has(viewer, MANAGE_PERMISSION) ? repository.all() : repository.ownedBy(viewer);
        return warps.stream().map(warp -> new OwnedWarp(warp.owner(), warp)).toList();
    }

    private ItemStack icon(PlayerRef viewer, OwnedWarp owned) {
        PlayerWarp warp = owned.warp();
        Position at = warp.location();
        Material material = warp.iconMaterial()
                .map(this::iconMaterial)
                .orElseGet(() -> layout.base().fallbackIcon());
        Map<String, String> placeholders = Map.of(
                "name", warp.name().value(),
                "owner", owned.owner().name(),
                "world", at.world().name(),
                "x", Long.toString(Math.round(at.x())),
                "y", Long.toString(Math.round(at.y())),
                "z", Long.toString(Math.round(at.z())),
                "visibility", visibilityWord(viewer, warp.isPublic()));
        return ItemBuilder.of(material)
                .name(guiText.text(viewer, PlayerwarpsMessageKey.PWARP_GUI_LIST_ENTRY_NAME, placeholders))
                .lore(guiText.text(viewer, PlayerwarpsMessageKey.PWARP_GUI_LIST_ENTRY_LORE, placeholders))
                .build();
    }

    private Material iconMaterial(String raw) {
        Material matched = Material.matchMaterial(raw);
        return matched != null ? matched : layout.base().fallbackIcon();
    }

    private String visibilityWord(PlayerRef viewer, boolean isPublic) {
        return messages.resolve(
                viewer,
                isPublic ? PlayerwarpsMessageKey.PWARP_GUI_VALUE_PUBLIC : PlayerwarpsMessageKey.PWARP_GUI_VALUE_PRIVATE,
                Map.of());
    }

    private void promptCreate(Player player, PlayerRef viewer) {
        ItemStack prompt = ItemBuilder.of(layout.createIcon())
                .name(guiText.text(viewer, PlayerwarpsMessageKey.PWARP_GUI_LIST_CREATE_PROMPT))
                .build();
        anvil.open(player, prompt, result -> handleCreate(player, viewer, result));
    }

    private void handleCreate(Player player, PlayerRef viewer, AnvilResult result) {
        if (!(result instanceof AnvilResult.Submitted submitted)
                || submitted.text().isBlank()) {
            scheduler.onEntity(viewer, () -> view(viewer).open(player, viewer));
            return;
        }
        // A warp created from the list belongs to the operator who opened it, at their feet — exactly /setpwarp.
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
        PlayerWarpName name = PlayerWarpName.of(submitted.text());
        scheduler.async(() -> {
            setPlayerWarp.set(viewer, name, at);
            scheduler.onEntity(viewer, () -> view(viewer).open(player, viewer));
        });
    }
}
