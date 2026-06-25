package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The immutable context handed to an {@link EditableProperty#onClick} when its button is clicked in an editor: the
 * live {@link Player} doing the clicking (so a property can open an anvil/confirm in their screen), the viewer's
 * {@link PlayerRef} (the locale and identity dimension for catalog text and use-case calls), the click kind
 * (left vs right, shift-held) so a stepper or cycle can read direction off it, a {@code reopen} hook the property
 * calls to redraw the editor after an asynchronous setter completes, a {@link SelectorOpener} the property uses to
 * open a picker as a menu-engine child window, and a {@link ConfirmOpener} it uses to gate a destructive step
 * behind an engine confirm child.
 *
 * <p>Both openers are always present: every editor runs on the menu-engine editor runtime, which builds this
 * context with the engine's selector and confirm openers. The enum/list/colour pickers ride the engine child-menu
 * capability through them, so a property never needs to know which window system painted its parent.
 *
 * <p>A property never touches the raw {@link InventoryClickEvent}; the click kind is captured into the two
 * boolean flags at construction so the editors stay unit-testable without forging a full Bukkit event.
 *
 * @param player the live player who clicked
 * @param viewer the viewer reference (locale + identity)
 * @param rightClick whether the click was a right-click (left-click otherwise)
 * @param shiftClick whether shift was held during the click
 * @param reopen redraws the editor for the viewer; a property runs it after a setter so the new value shows
 * @param opener opens a picker as an engine child window
 * @param confirmOpener opens a confirm as an engine child window
 */
@NullMarked
public record ClickContext(
        Player player,
        PlayerRef viewer,
        boolean rightClick,
        boolean shiftClick,
        Runnable reopen,
        SelectorOpener opener,
        ConfirmOpener confirmOpener) {

    public ClickContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(reopen, "reopen");
        Objects.requireNonNull(opener, "opener");
        Objects.requireNonNull(confirmOpener, "confirmOpener");
    }
}
