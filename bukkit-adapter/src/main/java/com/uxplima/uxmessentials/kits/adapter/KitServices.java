package com.uxplima.uxmessentials.kits.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategoryManagerView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategoryParentSelectorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategorySelectorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategorySettingsView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitManagerMenu;
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
 * @param claimKit {@code /kit <name>}
 * @param listKits {@code /kit list}
 * @param showKit {@code /kit show}
 * @param createKit {@code /kit create}
 * @param delKit {@code /kit del}
 * @param kitEditor {@code /kit editor}
 * @param kitReset {@code /kit reset}
 * @param kitMenu the read-only browse menu {@code /kit list} opens
 * @param kitPreview the read-only preview menu the {@code /kit show} GUI path opens
 * @param kitEditorView the editable window the {@code /kit editor} GUI path opens
 * @param kitManager the admin {@code /kit editor} manager menu, rendered through the menu engine
 * @param players name → ref resolution for the {@code /kit <name> <player>} and {@code /kit reset} targets
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
        @Nullable KitManagerMenu kitManager,
        PlayerLookup players,
        @Nullable KitCategoryManagerView kitCategoryManagerView,
        @Nullable KitCategorySettingsView kitCategorySettingsView,
        @Nullable KitCategorySelectorView kitCategorySelectorView,
        @Nullable KitCategoryParentSelectorView kitCategoryParentSelectorView) {

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
