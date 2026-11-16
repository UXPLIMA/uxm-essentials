package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.List;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.menu.providers.ContentClick;
import com.uxplima.uxmlib.menu.providers.ContentProvider;
import com.uxplima.uxmlib.menu.providers.ContentRegions;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.ContentRegionSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The item slots of a managed mirror window: seeded once from the snapshot the window was opened with, then owned by
 * the viewer until the window closes, at which point whatever is in them is handed over to be saved.
 *
 * <p>This is the dupe-safe half of {@code /invsee} and {@code /endersee}. The viewer moves stacks inside their own
 * private copy and never touches the target's live container, so no click can read or write a foreign inventory; the
 * write-back hands the region to {@link MirrorEdits}, which saves what changed or takes it back. Because the region is
 * what the viewer is physically holding items in, it is never repainted after the first draw: a redraw painted from
 * the snapshot would undo an edit, and one painted from a snapshot the viewer has emptied would mint it back.
 *
 * <p>A viewer without the modify node opens the same window view-only: every movement is refused, and since nothing
 * can change, the write-back hands over the same items that were there when it opened. So does a window whose edit
 * is no longer open.
 */
@NullMarked
final class MirrorContent implements ContentProvider {

    @Override
    public List<@Nullable ItemStack> render(MenuContext ctx, ContentRegionSpec region) {
        MirrorHolder holder = holder(ctx);
        return ContentRegions.copies(holder.snapshot(), MirrorWindow.slotCount(holder.kind()));
    }

    @Override
    public boolean repaintsOnRedraw() {
        return false;
    }

    @Override
    public boolean allows(MenuContext ctx, ContentRegionSpec region, ContentClick click) {
        return holder(ctx).editable();
    }

    @Override
    public void readBack(MenuContext ctx, ContentRegionSpec region, List<@Nullable ItemStack> contents) {
        MirrorHolder holder = holder(ctx);
        holder.writeBack(ContentRegions.toArray(contents, MirrorWindow.slotCount(holder.kind())));
    }

    private static MirrorHolder holder(MenuContext ctx) {
        return ctx.subject(MirrorHolder.class);
    }
}
