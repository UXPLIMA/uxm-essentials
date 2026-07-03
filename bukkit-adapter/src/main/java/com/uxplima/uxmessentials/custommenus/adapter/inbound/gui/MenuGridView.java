package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService;
import com.uxplima.uxmessentials.custommenus.adapter.MenuEditorService.EditOutcome;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.GridHandlers;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.GridSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.GridView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The slot-grid canvas of the {@code /menu editor}: a visual mirror of a menu's slots where an operator places, moves
 * and clears items before saving. It is a thin consumer of the engine's {@link Menus#openGrid} primitive — the canvas
 * is an engine {@code MenuHolder} window, every preview is rendered engine-side from a {@link MenuItemSpec}, and the
 * shift-click clear gates behind the engine's confirm — so no raw Bukkit inventory is ever built here and the grid
 * stays on the engine like every other editor surface.
 *
 * <p>An open clones the registered {@link MenuSpec} into a {@link MenuEditSession} held per viewer, so every edit
 * mutates a private working copy and the live menu changes only on Save (which validates through the P0 writer and
 * hot-reloads the single file, off the tick thread). The interactions mirror the OGUI editor: left-click a filled cell
 * opens its item editor (a P3 seam, a note for now), left-click an empty cell adds a default item and opens that same
 * seam, right-click picks a cell up and the next click drops it (moving, or swapping with the target), and shift-click
 * clears a cell behind a confirm. Selection is shown by glowing the picked-up cell.
 */
@NullMarked
public final class MenuGridView {

    /** The material a freshly placed item starts as — a neutral stone the operator then re-skins in the item editor. */
    private static final String DEFAULT_MATERIAL = "STONE";

    private static final Material EMPTY_ICON = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
    private static final Material BLOCKER_ICON = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material BACK_ICON = Material.BARRIER;
    private static final Material SAVE_ICON = Material.EMERALD;
    private static final Material NAV_ICON = Material.ARROW;

    /** The control-row column the back button sits in (columns 0 and 8 are the engine's prev/next). */
    private static final int BACK_COLUMN = 3;

    /** The control-row column the save button sits in. */
    private static final int SAVE_COLUMN = 5;

    private final Menus menus;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final MenuEditorService service;
    private final Function<String, Optional<MenuSpec>> specOf;
    private final BiConsumer<Player, String> onBack;

    /** One editing session per viewer, holding the clone being edited and the picked-up slot (null when none). */
    private final Map<UUID, GridEditState> states = new ConcurrentHashMap<>();

    public MenuGridView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            MenuEditorService service,
            Function<String, Optional<MenuSpec>> specOf,
            BiConsumer<Player, String> onBack) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.service = Objects.requireNonNull(service, "service");
        this.specOf = Objects.requireNonNull(specOf, "specOf");
        this.onBack = Objects.requireNonNull(onBack, "onBack");
    }

    /**
     * Open the slot grid for {@code menuId}: clone the registered spec into a fresh edit session for this viewer, then
     * show the canvas. A menu that is no longer registered simply tells the viewer so and opens nothing.
     */
    public void open(Player player, PlayerRef viewer, String menuId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(menuId, "menuId");
        MenuSpec spec = specOf.apply(menuId).orElse(null);
        if (spec == null) {
            player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_NOT_FOUND, Map.of("name", menuId)));
            return;
        }
        GridEditState state = new GridEditState(menuId, MenuEditSession.from(spec));
        states.put(viewer.uuid(), state);
        showGrid(viewer, state);
    }

    /** The session a viewer is editing, exposed so a test can read the working copy without a live save. */
    Optional<MenuEditSession> editSession(UUID viewer) {
        return Optional.ofNullable(states.get(viewer)).map(state -> state.session);
    }

    /** Build the grid spec from the current session and open it through the engine; reused on every re-open. */
    private void showGrid(PlayerRef viewer, GridEditState state) {
        GridSpec spec = new GridSpec(
                guiText.text(viewer, CustomMenusMessageKey.MENU_GRID_TITLE, titlePlaceholders(state)),
                state.session.rows(),
                () -> contentOf(state),
                icon(viewer, EMPTY_ICON, CustomMenusMessageKey.MENU_GRID_EMPTY),
                ItemBuilder.of(BLOCKER_ICON).name(Component.empty()).build(),
                icon(viewer, NAV_ICON, GuiMessageKey.PAGE_PREVIOUS),
                icon(viewer, NAV_ICON, GuiMessageKey.PAGE_NEXT),
                controls(viewer, state));
        GridHandlers handlers = new GridHandlers(
                (view, player, menuSlot, filled, kind) -> onSlot(viewer, state, view, player, menuSlot, filled, kind));
        menus.openGrid(viewer, spec, handlers);
    }

    private Map<String, String> titlePlaceholders(GridEditState state) {
        return Map.of("name", state.menuId, "rows", Integer.toString(state.session.rows()));
    }

    /** The control-row buttons: back to the overview, and save the edited session over the P0 write path. */
    private List<GridSpec.Control> controls(PlayerRef viewer, GridEditState state) {
        return List.of(
                new GridSpec.Control(
                        BACK_COLUMN,
                        icon(viewer, BACK_ICON, CustomMenusMessageKey.MENU_GRID_BACK),
                        player -> onBack.accept(player, state.menuId)),
                new GridSpec.Control(
                        SAVE_COLUMN,
                        icon(viewer, SAVE_ICON, CustomMenusMessageKey.MENU_GRID_SAVE),
                        player -> saveGrid(player, viewer, state)));
    }

    /**
     * The engine content map for the current draw: each occupied menu slot mapped to its item spec (the first item
     * wins a shared slot), with the picked-up slot's preview glowed so the selection is visible on the canvas.
     */
    private Map<Integer, MenuItemSpec> contentOf(GridEditState state) {
        Map<Integer, MenuItemSpec> content = new LinkedHashMap<>();
        for (Map.Entry<String, MenuItemSpec> entry : state.session.items().entrySet()) {
            for (int slot : entry.getValue().slots().slots()) {
                content.putIfAbsent(slot, entry.getValue());
            }
        }
        if (state.selected != null && content.containsKey(state.selected)) {
            content.put(state.selected, withGlow(content.get(state.selected)));
        }
        return content;
    }

    /**
     * Route a content-cell click. A pending pick-up takes priority — the next click drops it onto the clicked cell
     * (moving, or swapping with whatever is there). Otherwise a shift-click clears a filled cell (behind a confirm), a
     * right-click picks a filled cell up, a left-click on a filled cell opens its item editor (a P3 seam), and a
     * left-click on an empty cell adds a default item and opens that seam for it.
     */
    private void onSlot(
            PlayerRef viewer,
            GridEditState state,
            GridView view,
            Player player,
            int menuSlot,
            boolean filled,
            ClickKind kind) {
        if (state.selected != null) {
            drop(viewer, state, view, player, menuSlot);
            return;
        }
        if (kind == ClickKind.SHIFT_LEFT || kind == ClickKind.SHIFT_RIGHT) {
            if (filled) {
                promptClear(viewer, state, player, menuSlot);
            }
            return;
        }
        if (kind == ClickKind.RIGHT) {
            if (filled) {
                pickUp(viewer, state, view, player, menuSlot);
            }
            return;
        }
        if (filled) {
            openSlotEditor(viewer, player, menuSlot);
        } else {
            addDefault(viewer, state, view, player, menuSlot);
        }
    }

    /** Pick up the item at {@code menuSlot}, glowing it and waiting for the next click to place it. */
    private void pickUp(PlayerRef viewer, GridEditState state, GridView view, Player player, int menuSlot) {
        state.selected = menuSlot;
        player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_GRID_SELECTED, slot(menuSlot)));
        view.reRender();
    }

    /** Drop the picked-up item onto {@code target}: move it there, swapping with whatever already sits at the target. */
    private void drop(PlayerRef viewer, GridEditState state, GridView view, Player player, int target) {
        int from = state.selected == null ? target : state.selected;
        state.selected = null;
        if (from != target) {
            moveOrSwap(state, from, target);
            player.sendMessage(guiText.text(
                    viewer,
                    CustomMenusMessageKey.MENU_GRID_MOVED,
                    Map.of("from", Integer.toString(from), "to", Integer.toString(target))));
        }
        view.reRender();
    }

    /** Relocate the item at {@code from} to {@code target}, moving any item already at {@code target} back to {@code from}. */
    private void moveOrSwap(GridEditState state, int from, int target) {
        String fromId = idAt(state.session, from);
        if (fromId == null) {
            return;
        }
        String targetId = idAt(state.session, target);
        state.session.moveItem(fromId, new SlotSet(List.of(target)));
        if (targetId != null && !targetId.equals(fromId)) {
            state.session.moveItem(targetId, new SlotSet(List.of(from)));
        }
    }

    /** Add a default item at {@code menuSlot}, then open its item editor (the P3 seam) so it can be dressed up. */
    private void addDefault(PlayerRef viewer, GridEditState state, GridView view, Player player, int menuSlot) {
        String id = freshId(state.session);
        state.session.addItem(id, defaultItem(menuSlot));
        player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_GRID_ADDED, slot(menuSlot)));
        view.reRender();
        openSlotEditor(viewer, player, menuSlot);
    }

    /**
     * Clear the item at {@code menuSlot} behind the engine confirm. On confirm the item is removed from the session
     * and the grid re-opens (the confirm window replaced it), so the cleared cell shows its empty placeholder again.
     */
    private void promptClear(PlayerRef viewer, GridEditState state, Player player, int menuSlot) {
        String id = idAt(state.session, menuSlot);
        if (id == null) {
            return;
        }
        menus.confirm(
                viewer,
                guiText.text(viewer, CustomMenusMessageKey.MENU_GRID_CLEAR_CONFIRM, slot(menuSlot)),
                () -> confirmClear(viewer, state, player, id, menuSlot),
                () -> showGrid(viewer, state));
    }

    /** The confirm's yes branch: drop the item from the working copy, tell the viewer, and re-open the grid. */
    private void confirmClear(PlayerRef viewer, GridEditState state, Player player, String id, int menuSlot) {
        state.session.removeItem(id);
        player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_GRID_CLEARED, slot(menuSlot)));
        showGrid(viewer, state);
    }

    /**
     * The item-editor routing seam. The P3 item property editor opens here; for now it notes that editing the item's
     * material / name / lore / actions arrives in that phase, so a filled cell (and a freshly added one) has a real,
     * single place to route to that the next phase fills in.
     */
    private void openSlotEditor(PlayerRef viewer, Player player, int menuSlot) {
        player.sendMessage(guiText.text(viewer, CustomMenusMessageKey.MENU_GRID_SLOT_SOON, slot(menuSlot)));
    }

    /** Save the edited session over the P0 write path off the tick thread, then report the outcome to the viewer. */
    private void saveGrid(Player player, PlayerRef viewer, GridEditState state) {
        scheduler.async(() -> {
            EditOutcome outcome = service.saveSession(state.menuId, state.session);
            scheduler.onEntity(
                    viewer,
                    () -> player.sendMessage(
                            guiText.text(viewer, saveKey(outcome), Map.of("name", state.menuId, "missing", ""))));
        });
    }

    private static MessageKey saveKey(EditOutcome outcome) {
        return switch (outcome) {
            case SAVED -> CustomMenusMessageKey.MENU_SAVED;
            case INVALID_REFS -> CustomMenusMessageKey.MENU_SAVE_INVALID;
            default -> CustomMenusMessageKey.MENU_SAVE_FAILED;
        };
    }

    /** The id of the item occupying {@code slot} (the first found), or null when the slot holds nothing. */
    private static @Nullable String idAt(MenuEditSession session, int slot) {
        for (Map.Entry<String, MenuItemSpec> entry : session.items().entrySet()) {
            if (entry.getValue().slots().slots().contains(slot)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** A fresh {@code item-<n>} id not already used in the session, so a new placement never overwrites an item. */
    private static String freshId(MenuEditSession session) {
        int n = 1;
        while (session.item("item-" + n).isPresent()) {
            n++;
        }
        return "item-" + n;
    }

    /** A minimal valid item at {@code menuSlot}: a plain stone with no name, lore, decor or actions — a P3 canvas. */
    private static MenuItemSpec defaultItem(int menuSlot) {
        return new MenuItemSpec(
                new SlotSet(List.of(menuSlot)),
                0,
                DEFAULT_MATERIAL,
                "",
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.<Ref>of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    /** A copy of {@code item} with its enchant glow on — how the picked-up cell is shown as selected on the canvas. */
    private static MenuItemSpec withGlow(MenuItemSpec item) {
        ItemDecor decor = item.decor();
        ItemDecor glowing = new ItemDecor(decor.amount(), decor.modelData(), true, decor.flagTokens(), decor.meta());
        return new MenuItemSpec(
                item.slots(),
                item.priority(),
                item.material(),
                item.name(),
                item.lore(),
                glowing,
                item.loreMode(),
                item.view(),
                item.click(),
                item.update(),
                item.list(),
                item.type(),
                item.itemDrag());
    }

    /** A control / placeholder icon of {@code material} named from the viewer's catalog, carrying no inline literal. */
    private ItemStack icon(PlayerRef viewer, Material material, MessageKey name) {
        return ItemBuilder.of(material).name(guiText.text(viewer, name)).build();
    }

    private static Map<String, String> slot(int menuSlot) {
        return Map.of("slot", Integer.toString(menuSlot));
    }

    /** The per-viewer editing state: the menu being edited, its working copy, and the picked-up slot (null when none). */
    private static final class GridEditState {
        private final String menuId;
        private final MenuEditSession session;
        private @Nullable Integer selected;

        private GridEditState(String menuId, MenuEditSession session) {
            this.menuId = menuId;
            this.session = session;
        }
    }
}
