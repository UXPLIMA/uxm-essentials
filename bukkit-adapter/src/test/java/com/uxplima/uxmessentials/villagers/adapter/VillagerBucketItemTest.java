package com.uxplima.uxmessentials.villagers.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerBucketItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the captured-villager item: picking a villager up encodes its profession, level, and trades
 * into a tagged item, and placing it decodes those back onto a fresh villager unchanged; a plain item is not one of
 * ours.
 */
class VillagerBucketItemTest {

    private ServerMock server;
    private WorldMock world;
    private VillagerBucketItem bucket;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        bucket = new VillagerBucketItem();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void captureProducesATaggedItem() {
        ItemStack item =
                bucket.capture(villager(Villager.Profession.LIBRARIAN, 3), Component.text("Captured Villager"));

        assertThat(item.getType()).isEqualTo(Material.VILLAGER_SPAWN_EGG);
        assertThat(bucket.isBucket(item)).isTrue();
    }

    @Test
    void aPlainItemIsNotABucket() {
        assertThat(bucket.isBucket(new ItemStack(Material.VILLAGER_SPAWN_EGG))).isFalse();
        assertThat(bucket.isBucket(new ItemStack(Material.STONE))).isFalse();
    }

    @Test
    void placingRestoresTheSameProfessionLevelAndTrades() {
        Villager source = villager(Villager.Profession.LIBRARIAN, 3);
        source.setRecipes(List.of(
                recipe(new ItemStack(Material.DIAMOND, 2), new ItemStack(Material.EMERALD, 5)),
                recipe(new ItemStack(Material.BREAD), new ItemStack(Material.WHEAT, 3))));
        ItemStack item = bucket.capture(source, Component.text("Captured Villager"));

        Villager restored = villager(Villager.Profession.NONE, 1);
        bucket.restore(restored, item);

        assertThat(restored.getProfession()).isEqualTo(Villager.Profession.LIBRARIAN);
        assertThat(restored.getVillagerLevel()).isEqualTo(3);
        assertThat(restored.getRecipes()).hasSize(2);
        assertThat(restored.getRecipes().get(0).getResult()).isEqualTo(new ItemStack(Material.DIAMOND, 2));
        assertThat(restored.getRecipes().get(0).getIngredients()).containsExactly(new ItemStack(Material.EMERALD, 5));
        assertThat(restored.getRecipes().get(1).getResult()).isEqualTo(new ItemStack(Material.BREAD));
    }

    private Villager villager(Villager.Profession profession, int level) {
        Villager villager = (Villager) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.VILLAGER);
        villager.setProfession(profession);
        villager.setVillagerLevel(level);
        return villager;
    }

    private static MerchantRecipe recipe(ItemStack result, ItemStack ingredient) {
        MerchantRecipe recipe = new MerchantRecipe(result, 0, 9, false);
        recipe.addIngredient(ingredient);
        return recipe;
    }
}
