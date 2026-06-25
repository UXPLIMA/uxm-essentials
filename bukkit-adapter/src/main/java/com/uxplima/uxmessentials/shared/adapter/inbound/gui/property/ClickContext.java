package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The immutable context handed to an {@link EditableProperty#onClick} when its button is clicked in an editor: the
 * live {@link Player} doing the clicking (so a property can open an anvil/confirm in their screen), the viewer's
 * {@link PlayerRef} (the locale and identity dimension for catalog text and use-case calls), the click kind
 * (left vs right, shift-held) so a stepper or cycle can read direction off it, a {@code reopen} hook the property
 * calls to redraw the editor after an asynchronous setter completes, and an optional {@link SelectorOpener} the
 * property uses to open a picker as a menu-engine child window.
 *
 * <p>The opener is present only when the editor runs on the engine editor runtime; the legacy
 * {@code EntityEditorView} path builds its context through {@link #from} with no opener, so a property that opens a
 * picker falls back to its uxmLib selector there. This optional field is the single seam that lets the enum/list/
 * colour pickers ride the engine child-menu capability on the new runtime while staying on uxmLib on the old one.
 *
 * <p>A property never touches the raw {@link InventoryClickEvent}; the click kind is captured into the three
 * boolean flags at construction so the editors stay unit-testable without forging a full Bukkit event.
 *
 * @param player the live player who clicked
 * @param viewer the viewer reference (locale + identity)
 * @param rightClick whether the click was a right-click (left-click otherwise)
 * @param shiftClick whether shift was held during the click
 * @param reopen redraws the editor for the viewer; a property runs it after a setter so the new value shows
 * @param opener opens a picker as an engine child window, or null when the property must use its uxmLib fallback
 */
@NullMarked
public record ClickContext(
        Player player,
        PlayerRef viewer,
        boolean rightClick,
        boolean shiftClick,
        Runnable reopen,
        @Nullable SelectorOpener opener) {

    public ClickContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(reopen, "reopen");
    }

    /** A context with no selector opener — the legacy editor path, where a picker uses its uxmLib fallback. */
    public ClickContext(Player player, PlayerRef viewer, boolean rightClick, boolean shiftClick, Runnable reopen) {
        this(player, viewer, rightClick, shiftClick, reopen, null);
    }

    /** Build a context from a live click event plus the editor's reopen hook; no opener (legacy uxmLib path). */
    public static ClickContext from(InventoryClickEvent event, PlayerRef viewer, Runnable reopen) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(reopen, "reopen");
        Player player = (Player) event.getWhoClicked();
        return new ClickContext(player, viewer, event.isRightClick(), event.isShiftClick(), reopen, null);
    }
}
