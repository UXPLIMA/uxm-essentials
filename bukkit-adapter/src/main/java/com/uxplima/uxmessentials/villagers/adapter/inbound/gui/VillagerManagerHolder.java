package com.uxplima.uxmessentials.villagers.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.MerchantRecipe;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} tagging a {@code /villager manager} window so its listener recognises a click or close
 * as belonging to one of these editable views (and never to a vanilla container the editor happens to open) and can
 * reach the villager being edited, who is editing it, and the recipe set the villager had when the window opened. The
 * captured originals let the save borrow each row's use-limit / reward metadata and carry through any trade beyond the
 * visible rows; the captured villager position is where the save hops to apply the edits (the villager's own region).
 *
 * <p>The holder is built first and the menu is rendered against it; {@link #attach} then stores the built inventory so
 * {@link #getInventory()} can answer it, as Bukkit's holder contract expects.
 */
@NullMarked
final class VillagerManagerHolder implements InventoryHolder {

    private final PlayerRef editor;
    private final Villager villager;
    private final List<MerchantRecipe> originalRecipes;
    private final Position villagerPosition;
    private @Nullable Inventory inventory;

    VillagerManagerHolder(
            PlayerRef editor, Villager villager, List<MerchantRecipe> originalRecipes, Position villagerPosition) {
        this.editor = Objects.requireNonNull(editor, "editor");
        this.villager = Objects.requireNonNull(villager, "villager");
        this.originalRecipes = List.copyOf(originalRecipes);
        this.villagerPosition = Objects.requireNonNull(villagerPosition, "villagerPosition");
    }

    PlayerRef editor() {
        return editor;
    }

    Villager villager() {
        return villager;
    }

    /** The villager's recipe set as it stood when the window opened; the save borrows its metadata and tail. */
    List<MerchantRecipe> originalRecipes() {
        return originalRecipes;
    }

    /** The villager's region-anchoring position; the save hops here to touch the villager off the close thread. */
    Position villagerPosition() {
        return villagerPosition;
    }

    void attach(Inventory built) {
        this.inventory = Objects.requireNonNull(built, "built");
    }

    @Override
    public Inventory getInventory() {
        Inventory built = inventory;
        if (built == null) {
            throw new IllegalStateException("villager manager inventory not attached yet");
        }
        return built;
    }
}
