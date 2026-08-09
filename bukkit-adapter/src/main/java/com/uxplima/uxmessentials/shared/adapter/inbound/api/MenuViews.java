package com.uxplima.uxmessentials.shared.adapter.inbound.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.api.bukkit.menu.MenuClick;
import com.uxplima.uxmessentials.api.bukkit.menu.MenuClickKind;
import com.uxplima.uxmessentials.api.bukkit.menu.MenuView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import org.jspecify.annotations.NullMarked;

/**
 * The boundary between the menu engine's own runtime contexts and the published {@link MenuView} / {@link MenuClick}
 * interfaces a third-party handler is written against.
 *
 * <p>Two thin, immutable wrappers rather than copies: a render draws one context across every slot of a page, so
 * allocating a snapshot per handler call would put an object per slot per repaint on the render path. Wrapping keeps
 * the cost to one small object per call and never diverges from the context it wraps.
 *
 * <p>The engine's {@code ClickKind} is mapped by name onto the published {@link MenuClickKind}. The two enums carry
 * the same constants on purpose: the published one exists so the engine's spec model stays free to change, and
 * {@link #kindOf} is the single place the two are tied together, so a new gesture fails here rather than silently
 * reaching a consumer as the wrong one.
 */
@NullMarked
final class MenuViews {

    private MenuViews() {}

    static MenuView of(MenuContext ctx) {
        return new ContextView(ctx);
    }

    static MenuClick of(MenuActionContext action) {
        return new ActionClick(action);
    }

    /** The published gesture for an engine one; {@code ANY} never reaches a handler, so it is a wiring error here. */
    static MenuClickKind kindOf(ClickKind kind) {
        return switch (kind) {
            case LEFT -> MenuClickKind.LEFT;
            case RIGHT -> MenuClickKind.RIGHT;
            case SHIFT_LEFT -> MenuClickKind.SHIFT_LEFT;
            case SHIFT_RIGHT -> MenuClickKind.SHIFT_RIGHT;
            case MIDDLE -> MenuClickKind.MIDDLE;
            case DROP -> MenuClickKind.DROP;
            case CONTROL_DROP -> MenuClickKind.CONTROL_DROP;
            case DOUBLE_CLICK -> MenuClickKind.DOUBLE_CLICK;
            case ANY -> throw new IllegalStateException("ANY is a spec-side wildcard and never fires a click");
        };
    }

    private record ContextView(MenuContext ctx) implements MenuView {

        private ContextView {
            Objects.requireNonNull(ctx, "ctx");
        }

        @Override
        public UUID viewerId() {
            return ctx.viewer().uuid();
        }

        @Override
        public String viewerName() {
            return ctx.viewer().name();
        }

        @Override
        public UUID executorId() {
            return ctx.executor().uuid();
        }

        @Override
        public int page() {
            return ctx.page();
        }

        @Override
        public int pageCount() {
            return ctx.pageCount();
        }

        @Override
        public Map<String, String> arguments() {
            return ctx.arguments();
        }

        @Override
        public <T> Optional<T> entry(Class<T> type) {
            Objects.requireNonNull(type, "type");
            return ctx.entry().filter(type::isInstance).map(type::cast);
        }

        @Override
        public <T> Optional<T> subject(Class<T> type) {
            Objects.requireNonNull(type, "type");
            return ctx.subjectRaw().filter(type::isInstance).map(type::cast);
        }
    }

    private record ActionClick(MenuActionContext action) implements MenuClick {

        private ActionClick {
            Objects.requireNonNull(action, "action");
        }

        @Override
        public MenuView view() {
            return new ContextView(action.context());
        }

        @Override
        public Player player() {
            return action.player();
        }

        @Override
        public MenuClickKind kind() {
            return kindOf(action.clickKind());
        }

        @Override
        public Map<String, String> args() {
            return action.args();
        }

        @Override
        public String arg() {
            return action.arg();
        }
    }
}
