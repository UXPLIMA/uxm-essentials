package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.EditorRefresh;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.EditorState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuRefresh;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * The one entry point a feature uses to open a registered menu for a viewer. A feature registers its specs once at
 * wiring time, then calls {@link #open} to show one to a player. The façade first resolves every list source the
 * spec names off any tick thread — a list source may read a database, which must never block the viewer's region
 * thread on Folia — then hops onto the viewer's entity thread (where the live inventory may legally be touched),
 * builds the {@link MenuHolder} that owns every per-open piece of state, caches the resolved lists on it, renders
 * the spec into a fresh inventory the holder backs, and arms the refresh task. Pagination and refresh re-render
 * from the holder's cache, so a page flip never re-queries. The click listener recovers all of this from the
 * window alone, so no player-keyed side map is needed.
 */
public final class Menus {

    private final MenuRenderer renderer;
    private final Scheduler scheduler;
    private final ListSourceRegistry lists;

    /**
     * The editor renderer, present only on an engine wired for typed property editors. A list/spec-only engine (most
     * test fixtures) leaves it null and never calls {@link #openEditor}; opening an editor on such an engine is a
     * wiring error that fails loudly rather than half-rendering.
     */
    @Nullable private final EditorRenderer editorRenderer;

    private final Map<String, MenuSpec> specs = new ConcurrentHashMap<>();

    public Menus(MenuRenderer renderer, Scheduler scheduler, ListSourceRegistry lists) {
        this(renderer, scheduler, lists, null);
    }

    public Menus(
            MenuRenderer renderer,
            Scheduler scheduler,
            ListSourceRegistry lists,
            @Nullable EditorRenderer editorRenderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.lists = Objects.requireNonNull(lists, "lists");
        this.editorRenderer = editorRenderer;
    }

    /** Registers a parsed spec under its id; a feature does this once at wiring time. */
    public void registerSpec(String id, MenuSpec spec) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spec, "spec");
        specs.put(id, spec);
    }

    /**
     * Open the spec registered under {@code specId} for {@code viewer}, carrying {@code subject} as the domain
     * object the menu is about (or null for a subject-less menu). An unknown spec id is a coding error in the
     * caller's wiring, so it fails loudly here rather than opening an empty window a player would meet.
     */
    public void open(PlayerRef viewer, String specId, @Nullable Object subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(specId, "specId");
        MenuSpec spec = specs.get(specId);
        if (spec == null) {
            throw new IllegalArgumentException("no menu spec registered under id: " + specId);
        }
        MenuContext ctx = MenuContext.of(viewer, subject, 0);
        scheduler.async(() -> {
            Map<String, List<?>> resolved = resolveLists(spec, ctx);
            scheduler.onEntity(viewer, () -> openResolved(viewer, specId, spec, subject, resolved));
        });
    }

    /**
     * Open a typed property editor for {@code viewer} editing {@code subject}, as a holder-backed engine menu. It
     * builds the same {@link MenuHolder} every other menu uses — recognised and torn down by the one listener and
     * one {@code closeMenu} — but tags it with an {@link EditorState} so the listener routes its clicks through the
     * editor's property/button slots rather than a spec's. The window is shown on the viewer's entity thread, where
     * touching the live inventory is legal; unlike a list menu it queries no list source, so there is no off-thread
     * resolve step. An engine wired without an editor renderer cannot open an editor — that is a wiring error, so it
     * fails loudly here.
     */
    public void openEditor(PlayerRef viewer, EditorSpec spec, @Nullable Object subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        if (editorRenderer == null) {
            throw new IllegalStateException("this Menus engine was wired without editor support");
        }
        scheduler.onEntity(viewer, () -> openEditorResolved(viewer, spec, subject));
    }

    /** On the viewer's entity thread: build the editor holder + window, render the editor, show it. No refresh. */
    private void openEditorResolved(PlayerRef viewer, EditorSpec spec, @Nullable Object subject) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, subject, 0);
        MenuHolder holder = new MenuHolder(editorSpecId(spec), editorMenuSpec(spec), ctx);
        EditorState state = new EditorState(spec, subject);
        holder.attachEditor(state);
        Inventory inv = Bukkit.createInventory(holder, spec.layout().rows() * 9, spec.title(viewer, subject));
        holder.attach(inv);
        requireEditorRenderer().populate(inv, spec, state, live, viewer);
        live.openInventory(inv);
    }

    /**
     * Re-render an open editor in place — the {@code reopen} target a property's click hook runs after its setter.
     * It hops to the viewer's entity thread, confirms the live top inventory is still this holder's editor window,
     * clears the editor's slot routing, and repaints the same inventory from the live subject so the changed value
     * shows. No second {@code openInventory} and no new holder: the window the viewer is looking at is reused, so the
     * one listener and one teardown keep owning it.
     */
    public void reRenderEditor(MenuHolder holder) {
        Objects.requireNonNull(holder, "holder");
        EditorRefresh.reRender(holder, requireEditorRenderer(), scheduler);
    }

    private EditorRenderer requireEditorRenderer() {
        if (editorRenderer == null) {
            throw new IllegalStateException("this Menus engine was wired without editor support");
        }
        return editorRenderer;
    }

    /** A stable holder id for an editor open; editors are code-built and not registered, so the type name suffices. */
    private static String editorSpecId(EditorSpec spec) {
        return "editor:" + spec.getClass().getSimpleName();
    }

    /**
     * A minimal {@link MenuSpec} the editor holder carries so {@link MenuRefresh} and the holder's accessors have
     * something coherent to read: the editor's row count, refresh disabled (an editor is repainted on a click, never
     * on a timer), and no items (an editor's buttons live on its {@link EditorState}, not in a spec). The editor
     * render path never reads this spec's items, so an empty item map is exactly right.
     */
    private static MenuSpec editorMenuSpec(EditorSpec spec) {
        return new MenuSpec(
                "", spec.layout().rows(), new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Resolve every list source the spec names, keyed by source id. Runs off the viewer's region thread because a
     * source may read a database; an unregistered source resolves to an empty list so a wiring gap renders an empty
     * grid rather than failing the open. This is the only place a source is queried for one open.
     */
    private Map<String, List<?>> resolveLists(MenuSpec spec, MenuContext ctx) {
        Map<String, List<?>> resolved = new HashMap<>();
        for (MenuItemSpec item : spec.items().values()) {
            item.list().ifPresent(listSpec -> {
                String sourceId = listSpec.source().id();
                resolved.put(
                        sourceId, lists.get(sourceId).map(fn -> fn.apply(ctx)).orElse(List.of()));
            });
        }
        return resolved;
    }

    /** On the viewer's entity thread: build the holder-backed window, cache the lists, render, show, arm refresh. */
    private void openResolved(
            PlayerRef viewer, String specId, MenuSpec spec, @Nullable Object subject, Map<String, List<?>> resolved) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, subject, 0);
        MenuHolder holder = new MenuHolder(specId, spec, ctx);
        holder.setResolvedLists(resolved);
        Inventory inv = Bukkit.createInventory(holder, spec.rows() * 9, renderer.title(spec, ctx));
        holder.attach(inv);
        renderer.populate(inv, spec, ctx, holder::recordSlot, holder.resolvedLists());
        live.openInventory(inv);
        MenuRefresh.start(holder, scheduler, () -> reRender(holder));
    }

    /** Redraw an open menu in place on its viewer's thread, but only if that window is still this holder's. */
    private void reRender(MenuHolder holder) {
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            Player p = Bukkit.getPlayer(holder.ctx().viewer().uuid());
            if (p == null) {
                return;
            }
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder h) || h != holder) {
                return;
            }
            holder.clearClickMap();
            renderer.populate(
                    holder.getInventory(), holder.spec(), holder.ctx(), holder::recordSlot, holder.resolvedLists());
        });
    }

    /**
     * Close every menu this engine owns, cancelling its refresh task first so no timer survives the disable. The
     * online-roster sweep runs on the global region thread (the only thread on which the roster is coherent on
     * Folia), matching the scoreboard/tablist tear-down pattern. The click listener is uninstalled by the bootstrap
     * wiring, not here, so a closed window cannot be re-clicked.
     */
    public void shutdown() {
        scheduler.onGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                InventoryHolder open =
                        player.getOpenInventory().getTopInventory().getHolder();
                if (open instanceof MenuHolder holder) {
                    holder.cancelRefresh();
                    player.closeInventory();
                }
            }
        });
    }
}
