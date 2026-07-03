package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.RenderedSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import org.jspecify.annotations.Nullable;

/**
 * The single owner of everything one open menu needs: the spec it was built from, the live context (whose page
 * can change as the viewer pages through a list), the click map routing each filled slot back to the spec that
 * produced it, and the refresh task to stop when the menu closes. Being the {@link InventoryHolder} of its own
 * inventory is what lets the click listener recover all of this from the event alone — no player-keyed side map,
 * so nothing can leak when a player quits mid-menu.
 */
public final class MenuHolder implements InventoryHolder {

    private final String specId;

    private final MenuSpec spec;

    private MenuContext ctx;

    private final Map<Integer, RenderedSlot> clickMap = new HashMap<>();

    /**
     * The list-source entries this open resolved once off the viewer's region thread, keyed by source id. Every
     * redraw — a page flip, a refresh tick — re-renders from this cache rather than re-querying, so a database-backed
     * source is touched once per open and never on the region thread.
     */
    private Map<String, List<?>> resolvedLists = Map.of();

    @Nullable private Cancellable refreshHandle;

    @Nullable private Inventory inventory;

    /**
     * The viewer's own 36 inventory slots as they were the instant a bottom-inventory menu opened, saved so the
     * engine can put them back when the menu closes (or drop them in place of the menu tiles on death). Null for an
     * ordinary menu, which never touches the player inventory. It lives on the holder — GC'd with the open menu on
     * close — so the snapshot needs no player-keyed side map and cannot leak when a viewer quits; the close handler
     * threads the closing player in from the event, since the holder deliberately does not hold a live entity.
     */
    private @Nullable ItemStack @Nullable [] bottomSnapshot;

    /**
     * The clock reading of the last click this menu let through the anti-spam window, or {@code 0} before the first.
     * It lives on the holder — GC'd with the open menu on close — so the throttle needs no player-keyed side map and
     * nothing leaks when a viewer quits. Read and written only on the viewer's own entity thread inside the click
     * handler, so the check-and-stamp is single-threaded per holder and needs no lock.
     */
    private long lastClickMs;

    /**
     * Set only on the editor path: the editor's per-open property/subject state and its slot routing. A spec menu
     * leaves this null and routes clicks through {@link #clickMap}; an editor sets it and routes through it instead,
     * so the one listener tells the two apart by its presence.
     */
    @Nullable private EditorState editor;

    /**
     * Set only on the confirm path: the two-button confirm window's yes/no decisions. A spec or editor menu leaves
     * this null; a confirm window sets it and the one listener routes its clicks through it, so all three menu kinds
     * ride the same holder and the same teardown without a separate listener.
     */
    @Nullable private ConfirmState confirm;

    /**
     * Set only on the selector path: the option choices of a picker child window. A spec, editor, or confirm menu
     * leaves this null; a selector sets it and the one listener routes its clicks through it, so all four menu kinds
     * ride the same holder and the same teardown without a separate listener.
     */
    @Nullable private SelectorState selector;

    /**
     * Set only on the entity-list path: the paginated list's per-open entity/button slot routing. Any other menu
     * kind leaves this null; an entity list sets it and the one listener routes its clicks through it (an entity
     * icon to {@code onSelect}, the nav buttons to a re-paginate, create/action to their handlers), so all five
     * menu kinds ride the same holder and the same teardown without a separate listener.
     */
    @Nullable private ListViewState listView;

    /**
     * Set only on the grid path: the slot-grid canvas's per-open cell/control/nav routing. Any other menu kind leaves
     * this null; a grid sets it and the one listener routes its clicks through it, so all six menu kinds ride the same
     * holder and the same teardown without a separate listener.
     */
    @Nullable private GridViewState gridView;

    public MenuHolder(String specId, MenuSpec spec, MenuContext ctx) {
        this.specId = Objects.requireNonNull(specId, "specId");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    public String specId() {
        return specId;
    }

    public MenuSpec spec() {
        return spec;
    }

    public MenuContext ctx() {
        return ctx;
    }

    /** Swaps the live context — used when paging, where only the context's page changes. */
    public void setCtx(MenuContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    /** Binds the inventory this holder owns, once the engine has created it for this open. */
    public void attach(Inventory inv) {
        this.inventory = Objects.requireNonNull(inv, "inv");
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("inventory not attached for menu " + specId);
        }
        return inventory;
    }

    /** Drops every recorded slot ahead of a re-render so stale entries can never be clicked. */
    public void clearClickMap() {
        clickMap.clear();
    }

    /** Caches the list-source entries this open resolved off-thread; a defensive copy keeps the holder the owner. */
    public void setResolvedLists(Map<String, List<?>> resolved) {
        Objects.requireNonNull(resolved, "resolved");
        this.resolvedLists = Map.copyOf(resolved);
    }

    /** The cached list-source entries every redraw of this menu renders from, never re-querying the source. */
    public Map<String, List<?>> resolvedLists() {
        return resolvedLists;
    }

    public void recordSlot(int slot, RenderedSlot rs) {
        Objects.requireNonNull(rs, "rs");
        clickMap.put(slot, rs);
    }

    public Optional<RenderedSlot> clickAt(int slot) {
        return Optional.ofNullable(clickMap.get(slot));
    }

    /** Attaches the editor state for an editor-path open; a spec menu never calls this and leaves it null. */
    public void attachEditor(EditorState editor) {
        this.editor = Objects.requireNonNull(editor, "editor");
    }

    /** The editor state if this holder backs an editor, or empty for a spec menu. */
    public Optional<EditorState> editor() {
        return Optional.ofNullable(editor);
    }

    /** Attaches the confirm state for a confirm-window open; a spec or editor menu never calls this. */
    public void attachConfirm(ConfirmState confirm) {
        this.confirm = Objects.requireNonNull(confirm, "confirm");
    }

    /** The confirm state if this holder backs a confirm window, or empty for a spec or editor menu. */
    public Optional<ConfirmState> confirm() {
        return Optional.ofNullable(confirm);
    }

    /** Attaches the selector state for a picker-window open; a spec, editor, or confirm menu never calls this. */
    public void attachSelector(SelectorState selector) {
        this.selector = Objects.requireNonNull(selector, "selector");
    }

    /** The selector state if this holder backs a picker window, or empty for any other menu kind. */
    public Optional<SelectorState> selector() {
        return Optional.ofNullable(selector);
    }

    /** Attaches the list-view state for an entity-list open; any other menu kind never calls this. */
    public void attachListView(ListViewState listView) {
        this.listView = Objects.requireNonNull(listView, "listView");
    }

    /** The list-view state if this holder backs an entity list, or empty for any other menu kind. */
    public Optional<ListViewState> listView() {
        return Optional.ofNullable(listView);
    }

    /** Attaches the grid state for a slot-grid open; any other menu kind never calls this and leaves it null. */
    public void attachGridView(GridViewState gridView) {
        this.gridView = Objects.requireNonNull(gridView, "gridView");
    }

    /** The grid state if this holder backs a slot-grid canvas, or empty for any other menu kind. */
    public Optional<GridViewState> gridView() {
        return Optional.ofNullable(gridView);
    }

    /** The clock reading of the last click that passed this menu's anti-spam window, or {@code 0} before the first. */
    public long lastClickMs() {
        return lastClickMs;
    }

    /** Records the clock reading of a click the anti-spam window just let through. */
    public void lastClickMs(long now) {
        this.lastClickMs = now;
    }

    /**
     * Save the viewer's real bottom inventory taken when a bottom-inventory menu opened, or clear it (with a
     * {@code null}) once the restore has been scheduled/performed so a double close cannot restore twice. A defensive
     * copy keeps the holder the sole owner of the array.
     */
    public void setBottomSnapshot(@Nullable ItemStack @Nullable [] snapshot) {
        this.bottomSnapshot = snapshot == null ? null : snapshot.clone();
    }

    /** The saved real bottom inventory of a bottom-inventory menu, or null for an ordinary menu / after a restore. */
    public @Nullable ItemStack @Nullable [] bottomSnapshot() {
        return bottomSnapshot == null ? null : bottomSnapshot.clone();
    }

    public void setRefreshHandle(Cancellable handle) {
        this.refreshHandle = Objects.requireNonNull(handle, "handle");
    }

    /** Cancels the refresh task and forgets it. Idempotent: a second close is a harmless no-op. */
    public void cancelRefresh() {
        if (refreshHandle != null) {
            refreshHandle.cancel();
            refreshHandle = null;
        }
    }
}
