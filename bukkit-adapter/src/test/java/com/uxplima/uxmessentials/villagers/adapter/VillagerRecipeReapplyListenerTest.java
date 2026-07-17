package com.uxplima.uxmessentials.villagers.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.VillagerRecipeReapplyListener;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerRecipeStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the load-time reapply: when a villager carrying a manager-stored custom trade set enters a
 * world, its stored trades are reapplied over whatever recipes it currently holds; a villager with no stored set is
 * left untouched.
 */
class VillagerRecipeReapplyListenerTest {

    private ServerMock server;
    private WorldMock world;
    private Villager villager;
    private VillagerRecipeStore store;
    private VillagerRecipeReapplyListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        villager = (Villager) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.VILLAGER);
        store = new VillagerRecipeStore();
        listener = new VillagerRecipeReapplyListener(store);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aStoredTradeSetIsReappliedWhenTheVillagerEntersTheWorld() {
        store.store(villager, List.of(recipe(Material.DIAMOND, Material.EMERALD)));
        villager.setRecipes(List.of()); // vanilla handed it different (here, no) trades

        listener.onEntityAdd(new EntityAddToWorldEvent(villager, world));

        assertThat(villager.getRecipes()).hasSize(1);
        assertThat(villager.getRecipes().get(0).getResult().getType()).isEqualTo(Material.DIAMOND);
    }

    @Test
    void anUnmanagedVillagerIsLeftUntouched() {
        villager.setRecipes(List.of(recipe(Material.BREAD, Material.WHEAT)));

        listener.onEntityAdd(new EntityAddToWorldEvent(villager, world));

        assertThat(villager.getRecipes()).hasSize(1);
        assertThat(villager.getRecipes().get(0).getResult().getType()).isEqualTo(Material.BREAD);
    }

    private static MerchantRecipe recipe(Material result, Material cost) {
        MerchantRecipe recipe = new MerchantRecipe(new ItemStack(result), 0, 9, false);
        recipe.addIngredient(new ItemStack(cost));
        return recipe;
    }
}
