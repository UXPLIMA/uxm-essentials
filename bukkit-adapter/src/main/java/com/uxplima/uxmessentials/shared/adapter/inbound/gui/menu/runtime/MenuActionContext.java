package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * What an action binding receives on a click: the per-open {@link MenuContext} plus the live {@link Player} and
 * the gesture that fired. Actions need the live handle (to give items, play sounds, run commands) and the click
 * kind (to branch left vs. shift-right), neither of which belongs on the render-time {@link MenuContext}.
 *
 * <p>Public so feature bindings can read it; created by the engine on each click.
 */
public final class MenuActionContext {

    private final MenuContext ctx;

    private final Player player;

    private final ClickKind clickKind;

    public MenuActionContext(MenuContext ctx, Player player, ClickKind clickKind) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.player = Objects.requireNonNull(player, "player");
        this.clickKind = Objects.requireNonNull(clickKind, "clickKind");
    }

    public PlayerRef viewer() {
        return ctx.viewer();
    }

    public Player player() {
        return player;
    }

    public ClickKind clickKind() {
        return clickKind;
    }

    public int page() {
        return ctx.page();
    }

    public <T> T subject(Class<T> type) {
        return ctx.subject(type);
    }

    public <T> T entry(Class<T> type) {
        return ctx.entry(type);
    }
}
