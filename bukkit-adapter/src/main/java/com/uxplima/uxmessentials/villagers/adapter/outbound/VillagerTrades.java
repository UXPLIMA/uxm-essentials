package com.uxplima.uxmessentials.villagers.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;

import org.jspecify.annotations.NullMarked;

/**
 * The one side-effecting step the trade features share: reset a villager's merchant recipes so no trade is locked out.
 * A {@link MerchantRecipe} is exhausted when its {@code uses} reaches {@code maxUses}; setting {@code uses} back to
 * zero makes the trade available again. This helper owns that mutation for both the restock sweep (all of a villager's
 * recipes on the timer) and the trade listener (all recipes for infinite trading, the single traded recipe for
 * instant restock).
 *
 * <p>Recipe uses are reset in place and the recipe list is set back on the villager, so the change takes whether
 * {@link Villager#getRecipes()} hands back the live recipes or copies of them.
 */
@NullMarked
public final class VillagerTrades {

    private VillagerTrades() {}

    /** Reset every one of {@code villager}'s trade recipes to zero uses, so none is greyed out. */
    public static void restockAll(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        List<MerchantRecipe> recipes = villager.getRecipes();
        if (recipes.isEmpty()) {
            return;
        }
        List<MerchantRecipe> reset = new ArrayList<>(recipes.size());
        for (MerchantRecipe recipe : recipes) {
            recipe.setUses(0);
            reset.add(recipe);
        }
        villager.setRecipes(reset);
    }

    /** Reset a single {@code recipe} to zero uses, restocking the trade a player just used. */
    public static void restock(MerchantRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        recipe.setUses(0);
    }
}
