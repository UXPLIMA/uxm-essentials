package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.Locale;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;

/**
 * The generic action vocabulary every menu can lean on without a feature wiring it. Registered once at startup
 * into the shared {@link MenuBindings}, so a spec loaded from disk resolves {@code close} / {@code open} /
 * {@code command} / {@code message} / {@code sound} the same way a code-registered feature menu does. Each handler
 * runs as the clicking player — nothing here touches the console; that is gated separately so an operator menu
 * cannot dispatch privileged commands by default.
 */
public final class MenuVocabulary {

    private MenuVocabulary() {}

    /**
     * Register the generic, player-scoped actions into {@code bindings}. {@code menus} is needed only by the
     * {@code open} action, which opens another registered spec for the same viewer with no subject — an
     * operator-opened menu carries no domain object.
     */
    public static void registerActions(MenuBindings bindings, Menus menus) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(menus, "menus");
        bindings.action("close", ctx -> ctx.player().closeInventory());
        // v2: true previous-menu history is a later refinement, so back behaves as close for now.
        bindings.action("back", ctx -> ctx.player().closeInventory());
        bindings.action("open", ctx -> menus.open(ctx.viewer(), ctx.arg(), null));
        bindings.action("command", ctx -> ctx.player().performCommand(ctx.arg()));
        bindings.action("message", ctx -> ctx.player().sendMessage(StyledText.render(ctx.arg())));
        bindings.action("sound", MenuVocabulary::playSound);
    }

    /** Play {@code arg} as a sound key for the clicking player; a blank key is a no-op rather than an error. */
    private static void playSound(MenuActionContext ctx) {
        String key = ctx.arg();
        if (key.isBlank()) {
            return;
        }
        var at = Objects.requireNonNull(ctx.player().getLocation(), "player location");
        ctx.player().playSound(at, key.toLowerCase(Locale.ROOT), 1f, 1f);
    }
}
