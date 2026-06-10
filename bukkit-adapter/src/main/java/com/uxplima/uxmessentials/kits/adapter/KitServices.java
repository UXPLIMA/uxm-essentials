package com.uxplima.uxmessentials.kits.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategoryManagerView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategoryParentSelectorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategorySelectorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategorySettingsView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCreateChooserView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitManagerView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitMenuView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewView;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.CreateKit;
import com.uxplima.uxmessentials.kits.application.DelKit;
import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.KitReset;
import com.uxplima.uxmessentials.kits.application.ListKits;
import com.uxplima.uxmessentials.kits.application.ShowKit;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The constructed kits use cases the Brigadier commands share, built once per module start by
 * {@code KitsWiring} from the kernel ports, the config-backed repository, the PDC claim store, the Bukkit
 * granter, and the optional economy seam. Held so every command reads the same use cases; the kits context
 * keeps no other adapter-side runtime state, so there is nothing here to drain on stop beyond dropping this
 * holder.
 *
 * @param claimKit {@code /kit}
 * @param listKits {@code /kits}
 * @param showKit {@code /showkit}
 * @param createKit {@code /createkit}
 * @param delKit {@code /delkit}
 * @param kitEditor {@code /kiteditor}
 * @param kitReset {@code /kitreset}
 * @param kitMenu the read-only {@code /kits} browse menu the bare {@code /kits} command opens
 * @param kitPreview the read-only {@code /showkit} preview menu the GUI preview path opens
 * @param kitEditorView the editable {@code /kiteditor} window the GUI editor path opens
 * @param kitManagerView the admin {@code /kiteditor} manager GUI
 * @param players name → ref resolution for the {@code /kit <name> <player>} and {@code /kitreset} targets
 */
@NullMarked
public record KitServices(
        ClaimKit claimKit,
        ListKits listKits,
        ShowKit showKit,
        CreateKit createKit,
        DelKit delKit,
        KitEditor kitEditor,
        KitReset kitReset,
        KitMenuView kitMenu,
        KitPreviewView kitPreview,
        KitEditorView kitEditorView,
        @Nullable KitManagerView kitManagerView,
        PlayerLookup players,
        @Nullable KitCategoryManagerView kitCategoryManagerView,
        @Nullable KitCategorySettingsView kitCategorySettingsView,
        @Nullable KitCategorySelectorView kitCategorySelectorView,
        @Nullable KitCategoryParentSelectorView kitCategoryParentSelectorView,
        @Nullable KitCreateChooserView kitCreateChooserView) {

    public KitServices {
        Objects.requireNonNull(claimKit, "claimKit");
        Objects.requireNonNull(listKits, "listKits");
        Objects.requireNonNull(showKit, "showKit");
        Objects.requireNonNull(createKit, "createKit");
        Objects.requireNonNull(delKit, "delKit");
        Objects.requireNonNull(kitEditor, "kitEditor");
        Objects.requireNonNull(kitReset, "kitReset");
        Objects.requireNonNull(kitMenu, "kitMenu");
        Objects.requireNonNull(kitPreview, "kitPreview");
        Objects.requireNonNull(kitEditorView, "kitEditorView");
        Objects.requireNonNull(players, "players");
    }
}
