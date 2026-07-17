package com.uxplima.uxmessentials.villagers.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import com.uxplima.uxmessentials.villagers.domain.TradeRecipeDraft;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The fixed slot geometry of the trade-manager window and the pure translation between the window's item slots and a
 * villager's {@link MerchantRecipe} set. The window is a six-row chest: the top five rows are recipe rows — each
 * holds two editable buy slots, a decorative arrow, an editable sell slot, filler, and a remove button — and the
 * sixth is the control bar (the disable toggle and a help item). Editing a trade means dragging its buy/sell stacks
 * (their amounts are the trade amounts) into the row; filling an empty row adds a trade; clearing a row (the remove
 * button, or emptying it) drops that trade.
 *
 * <p>{@link #readRecipes} maps the window back to a recipe set on close: each visible row that forms a
 * {@link TradeRecipeDraft#isValid() valid} draft becomes a recipe, borrowing the use-limit and reward metadata of the
 * recipe that occupied that row when the window opened, and any trade the villager had beyond the five visible rows is
 * carried through untouched so a librarian's deeper trade list is never truncated by the editor.
 */
@NullMarked
final class VillagerManagerLayout {

    static final int SLOTS_PER_ROW = 9;
    static final int RECIPE_ROWS = 5;
    static final int ROWS = 6;
    static final int SIZE = ROWS * SLOTS_PER_ROW;

    static final int BUY_A_COL = 0;
    static final int BUY_B_COL = 1;
    static final int ARROW_COL = 2;
    static final int SELL_COL = 3;
    static final int REMOVE_COL = 8;

    static final int TOGGLE_SLOT = RECIPE_ROWS * SLOTS_PER_ROW; // control bar, first column
    static final int HELP_SLOT = SIZE - 1; // control bar, last column

    /** The use limit a manager-built recipe carries when its row had no prior recipe to borrow from. */
    private static final int DEFAULT_MAX_USES = 999;

    private VillagerManagerLayout() {}

    static int slot(int row, int col) {
        return row * SLOTS_PER_ROW + col;
    }

    static int rowOf(int slot) {
        return slot / SLOTS_PER_ROW;
    }

    /** Whether {@code slot} is one of a recipe row's three editable buy/sell slots. */
    static boolean isEditable(int slot) {
        if (slot < 0 || rowOf(slot) >= RECIPE_ROWS) {
            return false;
        }
        int col = slot % SLOTS_PER_ROW;
        return col == BUY_A_COL || col == BUY_B_COL || col == SELL_COL;
    }

    /** Whether {@code slot} is a recipe row's remove button. */
    static boolean isRemoveButton(int slot) {
        return slot >= 0 && rowOf(slot) < RECIPE_ROWS && slot % SLOTS_PER_ROW == REMOVE_COL;
    }

    /** Clear a recipe row's editable buy/sell slots, dropping that trade on the next save. */
    static void clearRow(Inventory inventory, int row) {
        Objects.requireNonNull(inventory, "inventory");
        inventory.setItem(slot(row, BUY_A_COL), null);
        inventory.setItem(slot(row, BUY_B_COL), null);
        inventory.setItem(slot(row, SELL_COL), null);
    }

    /**
     * Read the window back into a recipe set: each valid visible row becomes a recipe (borrowing the row's original
     * use-limit / reward metadata where one existed), followed by any of {@code originals} beyond the visible rows.
     */
    static List<MerchantRecipe> readRecipes(Inventory inventory, List<MerchantRecipe> originals) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(originals, "originals");
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (int row = 0; row < RECIPE_ROWS; row++) {
            readRow(inventory, row, originals).ifPresent(recipes::add);
        }
        for (int index = RECIPE_ROWS; index < originals.size(); index++) {
            recipes.add(originals.get(index));
        }
        return recipes;
    }

    private static java.util.Optional<MerchantRecipe> readRow(
            Inventory inventory, int row, List<MerchantRecipe> originals) {
        List<ItemStack> ingredients =
                ingredientsOf(inventory.getItem(slot(row, BUY_A_COL)), inventory.getItem(slot(row, BUY_B_COL)));
        ItemStack sell = inventory.getItem(slot(row, SELL_COL));
        TradeRecipeDraft draft = new TradeRecipeDraft(ingredients.size(), isPresent(sell));
        if (!draft.isValid() || sell == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(buildRecipe(sell, ingredients, template(originals, row)));
    }

    private static MerchantRecipe buildRecipe(
            ItemStack sell, List<ItemStack> ingredients, @Nullable MerchantRecipe template) {
        int maxUses = template != null ? template.getMaxUses() : DEFAULT_MAX_USES;
        boolean experience = template != null && template.hasExperienceReward();
        MerchantRecipe recipe = new MerchantRecipe(sell.clone(), 0, maxUses, experience);
        if (template != null) {
            recipe.setVillagerExperience(template.getVillagerExperience());
            recipe.setPriceMultiplier(template.getPriceMultiplier());
        }
        List<ItemStack> clones = new ArrayList<>(ingredients.size());
        for (ItemStack ingredient : ingredients) {
            clones.add(ingredient.clone());
        }
        recipe.setIngredients(clones);
        return recipe;
    }

    private static @Nullable MerchantRecipe template(List<MerchantRecipe> originals, int row) {
        return row < originals.size() ? originals.get(row) : null;
    }

    private static List<ItemStack> ingredientsOf(@Nullable ItemStack buyA, @Nullable ItemStack buyB) {
        List<ItemStack> ingredients = new ArrayList<>(TradeRecipeDraft.MAX_INGREDIENTS);
        if (isPresent(buyA)) {
            ingredients.add(buyA);
        }
        if (isPresent(buyB)) {
            ingredients.add(buyB);
        }
        return ingredients;
    }

    private static boolean isPresent(@Nullable ItemStack item) {
        return item != null && !item.getType().isAir();
    }
}
