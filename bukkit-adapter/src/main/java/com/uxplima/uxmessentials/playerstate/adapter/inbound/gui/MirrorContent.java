package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.List;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentClick;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentProvider;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentRegions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ContentRegionSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The item slots of a managed mirror window: seeded once from the snapshot the window was opened with, then owned by
 * the viewer until the window closes, at which point whatever is in them is reconciled onto the target.
 *
 * <p>This is the dupe-safe half of {@code /invsee} and {@code /endersee}. The viewer moves stacks inside their own
 * private copy and never touches the target's live container, so no click can read or write a foreign inventory; the
 * write-back then overwrites the target's slots from the region in one pass, so a relocation stays a relocation
 * rather than becoming a second copy. Because the region is what the viewer is physically holding items in, it is
 * never repainted after the first draw: a redraw painted from the snapshot would undo an edit, and one painted from
 * a snapshot the viewer has emptied would mint it back.
 *
 * <p>A viewer without the modify node opens the same window view-only: every movement is refused, and since nothing
 * can change, the write-back reconciles the same items that were there when it opened.
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
