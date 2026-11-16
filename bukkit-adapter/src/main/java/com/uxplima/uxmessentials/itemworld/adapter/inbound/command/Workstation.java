package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MenuType;

import com.uxplima.uxmessentials.shared.adapter.inbound.window.WindowShown;
import com.uxplima.uxmessentials.shared.application.permission.ItemworldPermissions;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The nine virtual workstations the itemworld surface opens as a single
 * enum, each carrying its command literal, an optional alias, its permission node, and the display name
 * rendered into {@link com.uxplima.uxmessentials.itemworld.domain.ItemworldMessageKey#WORKSTATION_OPENED}.
 * Modelling them as one enum keeps the nine commands one parameterised {@link WorkstationCommand} class rather
 * than nine near-identical files; the enum holds only immutable scalar data so the open behaviour lives in
 * {@link #open} as a {@code switch}, keeping the constants Error-Prone-clean.
 *
 * <p>Most stations open through a {@link MenuType} view created for the player with no backing block, the
 * non-deprecated {@code MenuType.Typed#create(HumanEntity)} path, so no real workstation is required. The
 * ender chest opens the player's own {@code getEnderChest()} inventory. {@link #open} must run on the player's
 * region thread.
 */
@NullMarked
public enum Workstation {
    ANVIL("anvil", null, ItemworldPermissions.WORKSTATION_ANVIL, "anvil"),
    WORKBENCH("workbench", "craft", ItemworldPermissions.WORKSTATION_WORKBENCH, "crafting table"),
    ENDERCHEST("enderchest", "echest", ItemworldPermissions.WORKSTATION_ENDERCHEST, "ender chest"),
    GRINDSTONE("grindstone", null, ItemworldPermissions.WORKSTATION_GRINDSTONE, "grindstone"),
    CARTOGRAPHY("cartography", null, ItemworldPermissions.WORKSTATION_CARTOGRAPHY, "cartography table"),
    LOOM("loom", null, ItemworldPermissions.WORKSTATION_LOOM, "loom"),
    SMITHINGTABLE("smithingtable", null, ItemworldPermissions.WORKSTATION_SMITHINGTABLE, "smithing table"),
    STONECUTTER("stonecutter", null, ItemworldPermissions.WORKSTATION_STONECUTTER, "stonecutter"),
    FURNACE("furnace", null, ItemworldPermissions.WORKSTATION_FURNACE, "furnace");

    private final String literal;
    private final @Nullable String alias;
    private final String permission;
    private final String displayName;

    Workstation(String literal, @Nullable String alias, String permission, String displayName) {
        this.literal = literal;
        this.alias = alias;
        this.permission = permission;
        this.displayName = displayName;
    }

    /** This station's command literal, public so the itemworld hub keys its launcher button slot/material by it. */
    public String literal() {
        return literal;
    }

    /** This station's permission node: public so the itemworld hub gates its launcher button by it. */
    public String permission() {
        return permission;
    }

    /** This station's display name: public so the itemworld hub renders its launcher button by it. */
    public String displayName() {
        return displayName;
    }

    List<String> aliases() {
        return alias == null ? List.of() : List.of(alias);
    }

    /**
     * Open this station's inventory view for {@code player} and say whether it is showing. Another plugin, or the
     * invsee edit guard, may cancel the open, and then the player is left on their own inventory, so a caller
     * reports the station only on {@code true}. Must run on the player's region thread.
     */
    public boolean open(Player player) {
        if (this == ENDERCHEST) {
            player.openInventory(player.getEnderChest());
            return WindowShown.showing(player, player.getEnderChest());
        }
        InventoryView view =
                switch (this) {
                    case ANVIL -> MenuType.ANVIL.create(player);
                    case WORKBENCH -> MenuType.CRAFTING.create(player);
                    case GRINDSTONE -> MenuType.GRINDSTONE.create(player);
                    case CARTOGRAPHY -> MenuType.CARTOGRAPHY_TABLE.create(player);
                    case LOOM -> MenuType.LOOM.create(player);
                    case SMITHINGTABLE -> MenuType.SMITHING.create(player);
                    case STONECUTTER -> MenuType.STONECUTTER.create(player);
                    case FURNACE -> MenuType.FURNACE.create(player);
                    case ENDERCHEST -> throw new IllegalStateException("the ender chest opens its own inventory");
                };
        player.openInventory(view);
        return WindowShown.showing(player, view.getTopInventory());
    }
}
