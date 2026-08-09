package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.util.List;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentProvider;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentRegions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ContentRegionSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The item slots of a snapshot preview: the stored snapshot decoded into the region's slots, painted as copies and
 * never taken out. The region is declared read-only, so the engine refuses every movement in it without asking; a
 * staff member reads what was stored and acts on it only through the window's explicit buttons.
 */
@NullMarked
final class SnapshotPreviewContent implements ContentProvider {

    @Override
    public List<@Nullable ItemStack> render(MenuContext ctx, ContentRegionSpec region) {
        SnapshotPreview preview = ctx.subject(SnapshotPreview.class);
        InventorySnapshotCodec.Decoded decoded =
                InventorySnapshotCodec.decode(preview.snapshot().contents());
        return ContentRegions.copies(decoded.contents(), SnapshotPreviewWindow.SNAPSHOT_SLOTS);
    }
}
