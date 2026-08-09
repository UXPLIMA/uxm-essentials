package com.uxplima.uxmessentials.villagers.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * One open {@code /villager manager} window: who is editing, the villager being edited, and the recipe set that
 * villager had when the window opened. It is the subject the menu carries, so every binding and the content provider
 * read what they need from here.
 *
 * <p>The captured originals let the save borrow each row's use-limit / reward metadata and carry through any trade
 * beyond the editable rows; the captured villager position is where the save hops to apply the edits (the villager's
 * own region, which on Folia may not be the closing editor's).
 */
@NullMarked
final class VillagerManagerHolder {

    private final PlayerRef editor;
    private final Villager villager;
    private final List<MerchantRecipe> originalRecipes;
    private final Position villagerPosition;

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
}
