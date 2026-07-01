package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.ListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ListViewRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.RenderedSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Requirement;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RequirementSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ChildClickHandler;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ConfirmOpener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorOpener;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The single listener every open menu routes through. It recognises a menu window by its {@link MenuHolder}, which
 * carries all per-open state, so the engine keeps no player-keyed side map and nothing can leak when a player quits
 * mid-menu. A click is always cancelled (menus never let an item be taken), then routed: a pagination button
 * re-renders the same holder on a new page, while any other slot fires the actions its spec bound to the gesture.
 * Actions run through the {@link Scheduler} entity hop so feature use-cases land on the viewer's region thread, and
 * the live {@link Player} is resolved from the viewer's UUID at that point — a viewer who logged off in the gap is
 * simply skipped.
 *
 * <p>Closing the window — whether the player closed it or quit with it open — funnels through one
 * {@link #closeMenu} choke-point that stops the refresh task. Both close paths are idempotent, so a double close is
 * a harmless no-op.
 */
@NullMarked
public final class MenuListener implements Listener {

    private final MenuRenderer renderer;
    private final ActionRegistry actions;
    private final ConditionRegistry conditions;
    private final Scheduler scheduler;
    private final Plugin plugin;

    /**
     * The editor renderer, present only on an engine wired for typed property editors. It is what a property's
     * reopen hook repaints the editor through; a spec-only engine leaves it null and never opens an editor, so the
     * editor branch is never reached.
     */
    @Nullable private final EditorRenderer editorRenderer;

    /**
     * The opener a property's click hook uses to show its picker as an engine child window, threaded into the
     * {@link ClickContext} on the editor path. Null on a spec-only engine and on an editor engine wired without it,
     * in which case a property that opens a picker falls back to its uxmLib selector — both runtimes coexist.
     */
    @Nullable private final SelectorOpener selectorOpener;

    /**
     * The opener a property's click hook uses to gate a destructive step behind an engine confirm child, threaded into
     * the {@link ClickContext} on the editor path alongside the selector opener. Null when the engine is wired without
     * it, in which case a property that confirms a removal falls back to its uxmLib {@code ConfirmMenu}.
     */
    @Nullable private final ConfirmOpener confirmOpener;

    /** Re-paints an entity list's page on a nav click; stateless, so one instance serves every list this routes. */
    private final ListViewRenderer listViewRenderer = new ListViewRenderer();

    public MenuListener(
            MenuRenderer renderer,
            ActionRegistry actions,
            ConditionRegistry conditions,
            Scheduler scheduler,
            Plugin plugin) {
        this(renderer, actions, conditions, scheduler, plugin, null, null, null);
    }

    public MenuListener(
            MenuRenderer renderer,
            ActionRegistry actions,
            ConditionRegistry conditions,
            Scheduler scheduler,
            Plugin plugin,
            @Nullable EditorRenderer editorRenderer) {
        this(renderer, actions, conditions, scheduler, plugin, editorRenderer, null, null);
    }

    public MenuListener(
            MenuRenderer renderer,
            ActionRegistry actions,
            ConditionRegistry conditions,
            Scheduler scheduler,
            Plugin plugin,
            @Nullable EditorRenderer editorRenderer,
            @Nullable SelectorOpener selectorOpener) {
        this(renderer, actions, conditions, scheduler, plugin, editorRenderer, selectorOpener, null);
    }

    public MenuListener(
            MenuRenderer renderer,
            ActionRegistry actions,
            ConditionRegistry conditions,
            Scheduler scheduler,
            Plugin plugin,
            @Nullable EditorRenderer editorRenderer,
            @Nullable SelectorOpener selectorOpener,
            @Nullable ConfirmOpener confirmOpener) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.editorRenderer = editorRenderer;
        this.selectorOpener = selectorOpener;
        this.confirmOpener = confirmOpener;
    }

    /** Registers this listener with the server. Called once when the menu engine starts. */
    public void install() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Unregisters every handler this listener holds. Called on engine stop so a reload leaves none dangling. */
    public void uninstall() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        int slot = event.getRawSlot();
        ConfirmState confirm = holder.confirm().orElse(null);
        if (confirm != null) {
            handleConfirmClick(holder, confirm, slot, (Player) event.getWhoClicked());
            return;
        }
        SelectorState selector = holder.selector().orElse(null);
        if (selector != null) {
            handleSelectorClick(
                    holder, selector, slot, (Player) event.getWhoClicked(), event.isRightClick(), event.isShiftClick());
            return;
        }
        EditorState editor = holder.editor().orElse(null);
        if (editor != null) {
            handleEditorClick(holder, editor, slot, event.isRightClick(), event.isShiftClick());
            return;
        }
        ListViewState list = holder.listView().orElse(null);
        if (list != null) {
            handleListClick(holder, list, slot);
            return;
        }
        holder.clickAt(slot).ifPresent(rs -> handleClick(holder, rs, event.getClick()));
    }

    /**
     * Route a click in a confirm window: the yes/no slot runs its decision exactly once. The single-fire guard on
     * the {@link ConfirmState} makes a stray second click in the same tick a no-op, and the window is closed before
     * the decision runs — mirroring uxmLib's {@code ConfirmMenu} — so a decision that opens another menu is not
     * clobbered by the close. The close funnels through the one {@code closeMenu}, so no second listener or teardown
     * path is introduced. A click on a non-button slot does nothing; the click is already cancelled.
     */
    private void handleConfirmClick(MenuHolder holder, ConfirmState confirm, int slot, Player clicker) {
        Runnable decision = confirm.decisionAt(slot).orElse(null);
        if (decision == null || !confirm.fire()) {
            return;
        }
        clicker.closeInventory();
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            if (Bukkit.getPlayer(holder.ctx().viewer().uuid()) != null) {
                decision.run();
            }
        });
    }

    /**
     * Route a click in a selector window: the clicked button runs its handler exactly once, given the click gesture
     * so a list-entry button can branch (an option/add/back button ignores it). The single-fire guard on the
     * {@link SelectorState} makes a stray second click in the same tick a no-op, and the window is closed before the
     * handler runs — mirroring the confirm flow — so a handler that reopens the parent editor (or the list child after
     * a mutation) is not clobbered by the close. The close funnels through the one {@code closeMenu}, so no second
     * listener or teardown path is introduced. A click on a non-button slot does nothing; the click is already
     * cancelled. The handler itself (the property's async-setter-then-reopen loop) is enqueued on the viewer's entity
     * thread, matching every other menu hop, and skipped if the viewer logged off in the gap.
     */
    private void handleSelectorClick(
            MenuHolder holder,
            SelectorState selector,
            int slot,
            Player clicker,
            boolean rightClick,
            boolean shiftClick) {
        ChildClickHandler choice = selector.chooseAt(slot).orElse(null);
        if (choice == null || !selector.fire()) {
            return;
        }
        clicker.closeInventory();
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            if (Bukkit.getPlayer(holder.ctx().viewer().uuid()) != null) {
                choice.onClick(rightClick, shiftClick);
            }
        });
    }

    /**
     * Route a click in an editor window: a property slot runs that property's {@link EditableProperty#onClick} with a
     * freshly built {@link ClickContext}, a plain button (back, delete) runs its recorded action. Both hop to the
     * viewer's entity thread first — the same hop a spec action takes — and re-resolve the live player there, so a
     * viewer who logged off in the gap is simply skipped and Bukkit is only ever touched on the owning thread. The
     * context's reopen hook repaints this editor in place, so the property's own setter-then-reopen loop redraws the
     * window the viewer is already looking at.
     */
    private void handleEditorClick(
            MenuHolder holder, EditorState editor, int slot, boolean rightClick, boolean shiftClick) {
        EditableProperty property = editor.propertyAt(slot).orElse(null);
        if (property != null) {
            runProperty(holder, property, rightClick, shiftClick);
            return;
        }
        editor.buttonAt(slot)
                .ifPresent(action -> scheduler.onEntity(holder.ctx().viewer(), () -> {
                    if (Bukkit.getPlayer(holder.ctx().viewer().uuid()) != null) {
                        action.run();
                    }
                }));
    }

    /** Hop to the viewer's entity thread, re-resolve the live player, and run one property's click there. */
    private void runProperty(MenuHolder holder, EditableProperty property, boolean rightClick, boolean shiftClick) {
        // A property click only reaches here when an editor is open, and an editor-capable listener is always wired
        // with both openers (the engine threads its own in); a missing one is a wiring error, surfaced here rather
        // than deep in the click context.
        SelectorOpener selector = Objects.requireNonNull(selectorOpener, "an editor listener needs a selector opener");
        ConfirmOpener confirm = Objects.requireNonNull(confirmOpener, "an editor listener needs a confirm opener");
        PlayerRef viewer = holder.ctx().viewer();
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live == null) {
                return;
            }
            Runnable reopen = () -> reRenderEditor(holder);
            property.onClick(new ClickContext(live, viewer, rightClick, shiftClick, reopen, selector, confirm));
        });
    }

    /** Repaint {@code holder}'s editor in place; a no-op when the engine was wired without editor support. */
    private void reRenderEditor(MenuHolder holder) {
        if (editorRenderer != null) {
            EditorRefresh.reRender(holder, editorRenderer, scheduler);
        }
    }

    /**
     * Route a click in an entity-list window: an entity icon runs the spec's {@code onSelect} for the entity drawn at
     * that slot on the current page, a previous/next nav button re-paginates the same holder in place, and a
     * create/action button runs its recorded handler. Each branch hops to the viewer's entity thread and re-resolves
     * the live player there — the same hop the editor and spec paths take — so a viewer who logged off in the gap is
     * simply skipped and Bukkit is only ever touched on the owning thread. A nav flip re-renders the live inventory
     * (no new window), so the one listener and one teardown keep owning it.
     */
    private void handleListClick(MenuHolder holder, ListViewState list, int slot) {
        if (list.isPrev(slot) || list.isNext(slot)) {
            int next = list.isPrev(slot)
                    ? Math.max(0, holder.ctx().page() - 1)
                    : holder.ctx().page() + 1;
            scheduler.onEntity(holder.ctx().viewer(), () -> repaginateList(holder, list, next));
            return;
        }
        Object entity = list.entityAt(slot).orElse(null);
        if (entity != null) {
            runOnList(holder, live -> ((ListSpec) list.spec()).onSelect().accept(live, entity));
            return;
        }
        list.buttonAt(slot).ifPresent(action -> runOnList(holder, ignored -> action.run()));
    }

    /** Hop to the viewer's entity thread, re-resolve the live player, and run one list handler there. */
    private void runOnList(MenuHolder holder, Consumer<Player> handler) {
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
            if (live != null) {
                handler.accept(live);
            }
        });
    }

    /** Re-paint an open entity list one page over, but only if that window is still this holder's list window. */
    private void repaginateList(MenuHolder holder, ListViewState list, int page) {
        Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
        if (live == null) {
            return;
        }
        if (!(live.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder h) || h != holder) {
            return;
        }
        list.clearSlots();
        int clamped = listViewRenderer.populate(
                holder.getInventory(),
                (ListSpec) list.spec(),
                list,
                live,
                holder.ctx().viewer(),
                page);
        holder.setCtx(holder.ctx().withPage(clamped));
    }

    private void handleClick(MenuHolder holder, RenderedSlot rs, ClickType click) {
        ItemType type = rs.item().type();
        if (type == ItemType.NEXT || type == ItemType.PREVIOUS || type == ItemType.JUMP) {
            navigate(holder, type);
            return;
        }
        ClickKind kind = kindOf(click);
        MenuContext base = rs.entry() == null ? holder.ctx() : holder.ctx().withEntry(rs.entry());
        if (!clickConditionsPass(rs.item().click(), kind, base)) {
            return;
        }
        RequirementSpec requirement = rs.item().click().requirementFor(kind);
        if (!requirementsPass(requirement, base)) {
            for (Ref denied : requirement.deny()) {
                runRef(holder, base, kind, denied);
            }
            return;
        }
        for (Ref ref : rs.item().click().actionsFor(kind)) {
            runRef(holder, base, kind, ref);
        }
    }

    /**
     * Whether {@code requirement}'s block passes for this click: at least {@link RequirementSpec#effectiveMinimum()} of
     * its requirements must hold, which gives AND / OR / N-of-M from one {@code minimum}. Each requirement's condition
     * is resolved against the condition registry (so a valued token like {@code has-money:100} reaches its handler with
     * {@code value=100}, the same registry-aware split the action path takes) and then negated when the requirement was
     * written with a leading {@code !}. An unregistered condition evaluates {@code false} — fail-closed — so a wiring
     * gap denies the click rather than silently granting it. An empty block passes: {@link RequirementSpec#NONE}
     * tallies zero passes against an effective minimum of zero.
     */
    private boolean requirementsPass(RequirementSpec requirement, MenuContext ctx) {
        int passes = 0;
        for (Requirement r : requirement.requirements()) {
            Ref eff = r.condition().resolve(conditions::has);
            boolean pass =
                    conditions.get(eff.id()).map(p -> p.test(ctx, eff.args())).orElse(false);
            if (pass != r.inverted()) {
                passes++;
            }
        }
        return passes >= requirement.effectiveMinimum();
    }

    /**
     * Whether every condition the spec bound to this gesture passes. Both the gesture's own conditions and the
     * shared {@link ClickKind#ANY} list must hold; an unregistered condition fails closed so a wiring gap blocks
     * the click rather than silently running it. An empty condition list always passes. Each ref is first resolved
     * against the condition registry, so a valued condition written {@code has-money:100} splits its head off and the
     * handler sees {@code value=100} in its args — the same registry-aware split the action path takes.
     */
    private boolean clickConditionsPass(ClickSpec click, ClickKind kind, MenuContext ctx) {
        for (Ref ref : merged(click.conditions().get(kind), click.conditions().get(ClickKind.ANY))) {
            Ref eff = ref.resolve(conditions::has);
            if (!conditions.get(eff.id()).map(p -> p.test(ctx, eff.args())).orElse(false)) {
                return false;
            }
        }
        return true;
    }

    private static List<Ref> merged(@Nullable List<Ref> own, @Nullable List<Ref> shared) {
        List<Ref> all = new ArrayList<>();
        if (own != null) {
            all.addAll(own);
        }
        if (shared != null) {
            all.addAll(shared);
        }
        return all;
    }

    /**
     * Run one bound action ref, applying its per-action modifiers. A failed chance roll denies the action: its
     * {@code deny} fallback (if any) runs through this same path and the main action is skipped. An action that
     * survives the roll is dispatched now, or after its delay — waited off-tick, then hopped back to the viewer's
     * entity thread. A ref with no modifiers (fires immediately, always, no fallback) takes the direct dispatch and
     * behaves exactly as the action loop did before modifiers existed.
     */
    private void runRef(MenuHolder holder, MenuContext base, ClickKind kind, Ref ref) {
        Ref effective = resolveEffective(ref);
        if (rolledDenied(effective)) {
            effective.deny().ifPresent(fallback -> runRef(holder, base, kind, fallback));
            return;
        }
        actions.get(effective.id()).ifPresent(handler -> {
            if (effective.delayTicks() > 0) {
                scheduler.asyncAfter(
                        Duration.ofMillis(effective.delayTicks() * 50L),
                        () -> dispatch(holder, base, kind, effective, handler));
            } else {
                dispatch(holder, base, kind, effective, handler);
            }
        });
    }

    /**
     * Resolve a bound ref to the effective ref the registry actually dispatches. {@link Ref#parse} is registry-blind:
     * it only splits an {@code id:value} token when the head is one of a few well-known generic prefixes, so a later
     * generic action written as {@code [give-money:100]} arrives here with the whole token as its id and would miss the
     * registry (a silent no-op). The authoritative split now lives on {@link Ref#resolve(java.util.function.Predicate)},
     * which this hands the action registry's {@code has}: a registered head re-splits (modifiers riding along), and a
     * feature ref ({@code economy:open-bank}) or an already-split generic ({@code sound:x}) is returned unchanged so its
     * dispatch stays byte-identical. The very same {@code Ref.resolve} gates the condition sites against the condition
     * registry, so a valued condition ({@code has-money:100}) reaches its handler the same way.
     */
    private Ref resolveEffective(Ref ref) {
        return ref.resolve(actions::has);
    }

    /**
     * Whether {@code ref}'s chance roll denied it. A full-chance ref never rolls (so it never denies); otherwise a
     * single {@code [0, 100)} draw decides, delegating the boundary to the pure {@link Ref#deniedAt} so the runtime
     * keeps no probability logic of its own.
     */
    private boolean rolledDenied(Ref ref) {
        if (ref.chance() >= 100.0) {
            return false;
        }
        return ref.deniedAt(ThreadLocalRandom.current().nextDouble(100.0));
    }

    /** Hop to the viewer's entity thread, re-resolve the live player, and run one bound action there. */
    private void dispatch(
            MenuHolder holder, MenuContext base, ClickKind kind, Ref ref, Consumer<MenuActionContext> handler) {
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
            if (live == null) {
                return;
            }
            handler.accept(new MenuActionContext(base, live, kind, ref.args(), new HolderControl(holder)));
        });
    }

    /**
     * The {@link MenuControl} the engine binds to one open window for the duration of a click: it drives that
     * holder's repaint through the same viewer's-entity-thread hop {@link #navigate} uses and the same
     * {@link #repaint}/{@link #repaginate} path a pagination click takes, so a menu-control action never touches the
     * inventory off the viewer's region thread and never opens a second window. It captures only the holder; the live
     * player is re-resolved inside each repaint, so a viewer who logged off in the gap is simply skipped there.
     */
    private final class HolderControl implements MenuControl {

        private final MenuHolder holder;

        HolderControl(MenuHolder holder) {
            this.holder = holder;
        }

        @Override
        public void refresh() {
            scheduler.onEntity(holder.ctx().viewer(), () -> repaint(holder));
        }

        @Override
        public void refreshSlot(int slot) {
            scheduler.onEntity(holder.ctx().viewer(), () -> refreshOneSlot(holder, slot));
        }

        @Override
        public void resetPagination() {
            scheduler.onEntity(holder.ctx().viewer(), () -> repaginate(holder, 0));
        }
    }

    /**
     * Re-render a single slot of the holder's window. The engine's {@code populate} path is whole-inventory — it has
     * no per-slot renderer yet — so a correct partial redraw is a full {@link #repaint}: broader than the caller
     * asked, but idempotent (the same spec and cached lists are redrawn) and never wrong. An out-of-range slot is a
     * no-op, so a spec typo cannot repaint on every click. When per-slot rendering lands this narrows to the one slot.
     */
    private void refreshOneSlot(MenuHolder holder, int slot) {
        if (slot < 0 || slot >= holder.getInventory().getSize()) {
            return;
        }
        repaint(holder);
    }

    /** Re-render the same holder one page over (next/previous), clamped at zero; jump is a v1 no-op target. */
    private void navigate(MenuHolder holder, ItemType type) {
        int page = holder.ctx().page();
        int newPage =
                switch (type) {
                    case NEXT -> page + 1;
                    case PREVIOUS -> Math.max(0, page - 1);
                    default -> page;
                };
        scheduler.onEntity(holder.ctx().viewer(), () -> repaginate(holder, newPage));
    }

    private void repaginate(MenuHolder holder, int newPage) {
        holder.setCtx(holder.ctx().withPage(newPage));
        repaint(holder);
    }

    /**
     * The one repaint path: redraw the holder's window in place at its current page, rebuilding the click routing
     * first so a stale slot can never be clicked. Both {@link #repaginate} (after moving the page) and a
     * {@code refresh} control action (at the current page) funnel through here, so there is a single place the engine
     * repaints a spec menu.
     */
    private void repaint(MenuHolder holder) {
        holder.clearClickMap();
        renderer.populate(
                holder.getInventory(), holder.spec(), holder.ctx(), holder::recordSlot, holder.resolvedLists());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder holder) {
            closeMenu(holder);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        InventoryHolder open =
                event.getPlayer().getOpenInventory().getTopInventory().getHolder();
        if (open instanceof MenuHolder holder) {
            closeMenu(holder);
        }
    }

    /**
     * The single teardown choke-point both close paths — the player closing the window and a quit with it open —
     * funnel through: it stops the refresh task so a timer can never re-render a closed window. Idempotent, so a
     * close that is immediately followed by a quit close is a harmless no-op.
     *
     * <p>Phase 2 adds generic close actions: this is where {@code spec.closeActions()} will be dispatched through
     * the {@link ActionRegistry}, branching on whether the menu was really closed versus reopened by navigation.
     * v1 has no generic action handlers, so teardown is the only work.
     */
    private void closeMenu(MenuHolder holder) {
        holder.cancelRefresh();
    }

    private static ClickKind kindOf(ClickType click) {
        return switch (click) {
            case LEFT -> ClickKind.LEFT;
            case RIGHT -> ClickKind.RIGHT;
            case SHIFT_LEFT -> ClickKind.SHIFT_LEFT;
            case SHIFT_RIGHT -> ClickKind.SHIFT_RIGHT;
            case MIDDLE -> ClickKind.MIDDLE;
            default -> ClickKind.LEFT;
        };
    }
}
