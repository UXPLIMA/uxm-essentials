package com.uxplima.uxmessentials.invrollback.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentRegions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The snapshot preview as the operator sees it: {@code modules/invrollback/gui/snapshot-preview.conf} plus the
 * bindings behind it. The file owns the window's height, its backdrop and where the details panel and the three
 * action buttons sit; {@link SnapshotPreviewView} owns the wording resolved from the message catalog and what each
 * button does, and {@link SnapshotPreviewContent} owns the block of slots the file hands over as a read-only
 * {@code content {}} region for the snapshot's own items.
 *
 * <p>The region's slot order is the snapshot's own, so a file that declared the wrong number of slots would show
 * items in the wrong places. That is refused at wiring time rather than discovered by a staff member reading a
 * misleading preview.
 */
@NullMarked
public final class SnapshotPreviewWindow {

    static final String SPEC_ID = "invrollback-snapshot-preview";
    static final String SPEC_RESOURCE = "modules/invrollback/gui/snapshot-preview.conf";
    static final String REGION = "invrollback:snapshot";

    /** The snapshot's own slot count: the main inventory plus armour and offhand, as it was recorded. */
    static final int SNAPSHOT_SLOTS = 45;

    /** The height the bundled spec is written for; a file that omits {@code rows} falls back to it. */
    private static final int ROWS = 6;

    private final Menus menus;
    private final MenuSpec spec;

    public SnapshotPreviewWindow(Menus menus, Path dataFolder, Logger log) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.spec = MenuSpecs.loadOrBundled(SPEC_RESOURCE, Objects.requireNonNull(dataFolder, "dataFolder"), ROWS, log);
        List<Integer> slots = ContentRegions.slots(spec, REGION, SPEC_RESOURCE);
        if (slots.size() != SNAPSHOT_SLOTS) {
            throw new IllegalStateException(SPEC_RESOURCE + ": the '" + REGION + "' region must declare exactly "
                    + SNAPSHOT_SLOTS + " slots, one per slot the snapshot recorded, but declares " + slots.size()
                    + "; a shorter or longer region would show the snapshot's items in the wrong places");
        }
    }

    /** Give the spec its behaviour and register it; called once at wiring time, with the view the buttons drive. */
    void register(MenuBindings bindings, SnapshotPreviewView view) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(view, "view");
        bindings.placeholder(
                "invrollback_preview_title",
                ctx -> view.previewTitle(ctx.subject(SnapshotPreview.class), ctx.viewer()));
        bindings.placeholder(
                "invrollback_info_lore", ctx -> view.infoLore(ctx.subject(SnapshotPreview.class), ctx.viewer()));
        bindings.action(
                "invrollback:restore",
                action -> view.onRestoreClick(action.subject(SnapshotPreview.class), action.player()));
        bindings.action(
                "invrollback:teleport",
                action -> view.onTeleportClick(action.subject(SnapshotPreview.class), action.player()));
        bindings.action(
                "invrollback:export",
                action -> view.onExportClick(action.subject(SnapshotPreview.class), action.player()));
        bindings.content(REGION, new SnapshotPreviewContent());
        menus.registerSpec(SPEC_ID, spec);
    }

    /** Show this window to {@code preview}'s viewer, carrying the preview as the menu's subject. */
    void open(SnapshotPreview preview) {
        menus.open(preview.staff(), SPEC_ID, preview);
    }
}
