package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;

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
     * Register the generic actions into {@code bindings}. {@code menus} is needed only by the {@code open} action,
     * which opens another registered spec for the same viewer with no subject — an operator-opened menu carries no
     * domain object. The {@code console} action is always registered so a spec referencing it still passes startup
     * validation, but it only dispatches when {@code allowConsole} is true; otherwise it no-ops and warns through
     * {@code log} so an operator who forgot the {@code custommenus.allow-console} flag sees why nothing happened.
     */
    public static void registerActions(MenuBindings bindings, Menus menus, boolean allowConsole, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(log, "log");
        bindings.action("close", ctx -> ctx.player().closeInventory());
        // v2: true previous-menu history is a later refinement, so back behaves as close for now.
        bindings.action("back", ctx -> ctx.player().closeInventory());
        bindings.action("open", ctx -> menus.open(ctx.viewer(), ctx.arg(), null));
        bindings.action("command", ctx -> ctx.player().performCommand(ctx.arg()));
        bindings.action("console", consoleAction(allowConsole, log));
        bindings.action("message", ctx -> ctx.player().sendMessage(StyledText.render(ctx.arg())));
        bindings.action("sound", MenuVocabulary::playSound);
    }

    /**
     * The {@code console} handler: dispatch {@code arg} from the console sender when {@code allowConsole}, else log
     * a warning naming the ignored command and do nothing — privileged dispatch stays opt-in.
     */
    private static Consumer<MenuActionContext> consoleAction(boolean allowConsole, Logger log) {
        if (allowConsole) {
            return ctx -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ctx.arg());
        }
        return ctx ->
                log.warn("menu console action is disabled (custommenus.allow-console=false); ignored: {}", ctx.arg());
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
