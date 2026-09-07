package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.jspecify.annotations.NullMarked;

/**
 * Who <em>triggered</em> a menu open, as opposed to who sees it.
 *
 * <p>The engine does not answer this and must not: an open can be triggered by a player, by the console or by
 * another plugin, so there is no live player to name and no meaning a general library could read. It carries the
 * value in {@link MenuContext#passthrough()} instead, an opaque map it never reads, and this is the one place this
 * plugin puts a value in and takes it back out.
 *
 * <p>It is deliberately not an argument. {@link MenuContext#arguments()} is text the player typed at the keyboard,
 * and those values are substituted into {@code command} actions, so a menu that declared an argument named
 * {@code executor} would let the player rewrite its own provenance.
 *
 * <p>{@link #of} falls back to the viewer, which is what keeps every ordinary open unchanged: a self-open attaches
 * nothing and {@code %executor%} reads exactly as {@code %player%} does, the way it always has.
 */
@NullMarked
public final class MenuExecutor {

    /** The passthrough key the opener travels under. */
    public static final String KEY = "executor";

    private MenuExecutor() {}

    /** A passthrough map attaching {@code executor} as the opener of the menu it is passed to. */
    public static Map<String, Object> attach(PlayerRef executor) {
        Objects.requireNonNull(executor, "executor");
        return Map.of(KEY, executor);
    }

    /** The opener this menu was opened by, or the viewer when the open attached none. */
    public static PlayerRef of(MenuContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        Object attached = ctx.passthrough().get(KEY);
        return attached instanceof PlayerRef executor ? executor : BukkitRefs.toRef(ctx.viewer());
    }
}
