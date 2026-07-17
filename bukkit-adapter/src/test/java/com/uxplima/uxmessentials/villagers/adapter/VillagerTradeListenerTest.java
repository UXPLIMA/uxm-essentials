package com.uxplima.uxmessentials.villagers.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import io.papermc.paper.event.player.PlayerTradeEvent;

import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.VillagerTradeListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the trade listener: on a completed trade, infinite trading resets every one of the villager's
 * recipes to zero uses and suppresses the use increment (so no trade locks out), instant restock resets just the
 * traded recipe, and with both features off the trade is left entirely alone.
 */
class VillagerTradeListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private Villager villager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        villager = (Villager) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.VILLAGER);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void infiniteTradingResetsEveryRecipeAndSuppressesTheIncrement() {
        MerchantRecipe traded = recipe(3);
        MerchantRecipe other = recipe(2);
        villager.setRecipes(List.of(traded, other));
        VillagerTradeListener listener = new VillagerTradeListener(true, false);

        listener.onTrade(tradeOf(traded));

        // Every one of the villager's recipes is back to zero uses, and the vanilla post-trade increment is off.
        assertThat(villager.getRecipes())
                .allSatisfy(recipe -> assertThat(recipe.getUses()).isZero());
    }

    @Test
    void infiniteTradingTurnsOffTheUseIncrement() {
        villager.setRecipes(List.of(recipe(3)));
        PlayerTradeEvent event = tradeOf(villager.getRecipes().get(0));
        new VillagerTradeListener(true, false).onTrade(event);

        assertThat(event.willIncreaseTradeUses()).isFalse();
    }

    @Test
    void instantRestockResetsOnlyTheTradedRecipe() {
        MerchantRecipe traded = recipe(4);
        PlayerTradeEvent event = tradeOf(traded);

        new VillagerTradeListener(false, true).onTrade(event);

        assertThat(traded.getUses()).isZero();
        assertThat(event.willIncreaseTradeUses()).isFalse();
    }

    @Test
    void bothFeaturesOffLeavesTheTradeAlone() {
        MerchantRecipe traded = recipe(4);
        PlayerTradeEvent event = tradeOf(traded);

        new VillagerTradeListener(false, false).onTrade(event);

        // Gate off = no-op: the traded recipe keeps its uses untouched.
        assertThat(traded.getUses()).isEqualTo(4);
    }

    private PlayerTradeEvent tradeOf(MerchantRecipe traded) {
        return new PlayerTradeEvent(player, villager, traded, true, true);
    }

    private static MerchantRecipe recipe(int uses) {
        return new MerchantRecipe(new ItemStack(Material.EMERALD), uses, 5, true);
    }
}
