package com.uxplima.uxmessentials.villagers.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerRecipeCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Round-trips a villager's custom trade set through the codec: the buy ingredients (with their amounts), the sell
 * result (with its amount), and the max-uses limit all survive an encode/decode, and an empty set stays empty.
 */
class VillagerRecipeCodecTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMultiIngredientRecipeRoundTrips() {
        MerchantRecipe recipe = new MerchantRecipe(new ItemStack(Material.DIAMOND, 2), 0, 12, true);
        recipe.addIngredient(new ItemStack(Material.EMERALD, 5));
        recipe.addIngredient(new ItemStack(Material.WHEAT, 3));

        List<MerchantRecipe> decoded = VillagerRecipeCodec.decode(VillagerRecipeCodec.encode(List.of(recipe)));

        assertThat(decoded).hasSize(1);
        MerchantRecipe out = decoded.get(0);
        assertThat(out.getResult()).isEqualTo(new ItemStack(Material.DIAMOND, 2));
        assertThat(out.getIngredients())
                .containsExactly(new ItemStack(Material.EMERALD, 5), new ItemStack(Material.WHEAT, 3));
        assertThat(out.getMaxUses()).isEqualTo(12);
    }

    @Test
    void manyRecipesKeepTheirOrder() {
        MerchantRecipe first = single(Material.STICK, Material.COAL);
        MerchantRecipe second = single(Material.BREAD, Material.IRON_INGOT);

        List<MerchantRecipe> decoded = VillagerRecipeCodec.decode(VillagerRecipeCodec.encode(List.of(first, second)));

        assertThat(decoded).hasSize(2);
        assertThat(decoded.get(0).getResult().getType()).isEqualTo(Material.STICK);
        assertThat(decoded.get(1).getResult().getType()).isEqualTo(Material.BREAD);
    }

    @Test
    void anEmptySetStaysEmpty() {
        assertThat(VillagerRecipeCodec.decode(VillagerRecipeCodec.encode(List.of())))
                .isEmpty();
    }

    private static MerchantRecipe single(Material result, Material cost) {
        MerchantRecipe recipe = new MerchantRecipe(new ItemStack(result), 0, 9, false);
        recipe.addIngredient(new ItemStack(cost));
        return recipe;
    }
}
