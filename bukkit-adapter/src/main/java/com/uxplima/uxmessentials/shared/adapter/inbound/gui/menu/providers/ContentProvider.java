package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.util.List;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ContentRegionSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The feature side of a menu's {@code content {}} region: what fills those slots, which movements the viewer may
 * make in them, and what becomes of whatever is left in them when the window closes. A feature registers one under
 * an id ({@code MenuBindings.content}), a spec names that id in its {@code content {}} block, and the engine does
 * the rest: it paints the region on every draw, routes its clicks here instead of to the spec's items, and hands
 * the final contents back on close.
 *
 * <p>This is the seam that lets a screen holding real item stacks (a trade offer, an inventory mirror, a kit's
 * contents) be laid out in a conf file: the frame, the buttons and the title are ordinary spec items an operator
 * re-skins, and only the live items stay in code, where the rules that stop an item being duplicated live.
 *
 * <p>Every method runs on the viewer's own entity thread, inside the click or close event, so an implementation
 * may read and write the open inventory directly but must not block.
 */
@NullMarked
public interface ContentProvider {

    /**
     * The stacks to paint into the region, in the region's declared slot order: index 0 fills its first slot, and a
     * shorter list (or a null element) leaves the remaining slots empty. Called on every draw of the menu, so it
     * must be cheap and must not query a database.
     */
    List<@Nullable ItemStack> render(MenuContext ctx, ContentRegionSpec region);

    /**
     * Whether this region is repainted from {@link #render} every time the menu is redrawn, or filled once when the
     * window opens and then left to the viewer. A region that projects something the feature owns (the mirrored half
     * of a trade, a read-only inventory view) repaints, so a change to the model shows up. A region the viewer
     * physically fills must not: between the moment a stack is placed and the moment the feature reads the region
     * back, a redraw painted from the model would wipe what was just put down, and a redraw painted from a model the
     * viewer has already emptied would mint it back. For such a region the window itself is the truth until it is
     * read back, so the engine leaves its slots alone after the first paint.
     */
    default boolean repaintsOnRedraw() {
        return true;
    }

    /**
     * Whether {@code click} may go through, i.e. whether vanilla should be allowed to perform that one movement.
     * Only ever asked for an {@code editable} region, and asked per click, so a provider can allow taking an item
     * out while refusing to put one in. The default refuses everything: a region is inert until its feature says
     * otherwise, which is what keeps a mis-declared spec from opening a hole.
     */
    default boolean allows(MenuContext ctx, ContentRegionSpec region, ContentClick click) {
        return false;
    }

    /**
     * Hand the region's final contents back when the window closes, in the region's declared slot order. This is
     * where a feature returns unclaimed items to the player or persists them; the window itself is discarded right
     * after, so anything not taken here is gone. The default does nothing, which is right for a read-only region
     * whose stacks are copies of something the feature already owns.
     */
    default void readBack(MenuContext ctx, ContentRegionSpec region, List<@Nullable ItemStack> contents) {}
}
