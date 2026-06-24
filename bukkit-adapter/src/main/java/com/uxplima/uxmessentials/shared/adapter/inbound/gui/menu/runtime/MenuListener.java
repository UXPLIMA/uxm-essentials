package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.RenderedSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
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

    public MenuListener(
            MenuRenderer renderer,
            ActionRegistry actions,
            ConditionRegistry conditions,
            Scheduler scheduler,
            Plugin plugin) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
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
        holder.clickAt(slot).ifPresent(rs -> handleClick(holder, rs, event.getClick()));
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
        for (Ref ref : rs.item().click().actionsFor(kind)) {
            actions.get(ref.id()).ifPresent(handler -> runAction(holder, base, kind, ref, handler));
        }
    }

    /**
     * Whether every condition the spec bound to this gesture passes. Both the gesture's own conditions and the
     * shared {@link ClickKind#ANY} list must hold; an unregistered condition fails closed so a wiring gap blocks
     * the click rather than silently running it. An empty condition list always passes.
     */
    private boolean clickConditionsPass(ClickSpec click, ClickKind kind, MenuContext ctx) {
        for (Ref ref : merged(click.conditions().get(kind), click.conditions().get(ClickKind.ANY))) {
            if (!conditions.get(ref.id()).map(p -> p.test(ctx, ref.args())).orElse(false)) {
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

    /** Hop to the viewer's entity thread, re-resolve the live player, and run one bound action there. */
    private void runAction(
            MenuHolder holder, MenuContext base, ClickKind kind, Ref ref, Consumer<MenuActionContext> handler) {
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
            if (live == null) {
                return;
            }
            handler.accept(new MenuActionContext(base, live, kind, ref.args()));
        });
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
