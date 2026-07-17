package com.uxplima.uxmessentials.villagers.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerRecipeStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the trade-manager window. The window mirrors the villager's recipes into its rows, an edit to
 * a sell slot and a filled empty row apply to the live merchant on close, the remove control drops a trade, and the
 * edited set is PDC-serialised so reapplying it (as the load listener would) yields the same trades. The disable toggle
 * flips the villager's per-villager flag. The scheduler is synchronous, so the close-time apply runs inline.
 */
class VillagerManagerViewTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private PlayerRef editor;
    private Villager villager;
    private PdcVillagerFlags flags;
    private VillagerRecipeStore store;
    private VillagerManagerView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        editor = new PlayerRef(player.getUniqueId(), player.getName());
        villager = (Villager) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.VILLAGER);
        flags = new PdcVillagerFlags();
        store = new VillagerRecipeStore();
        view = new VillagerManagerView(new GuiText(new KeyMessages()), new SyncScheduler(), flags, store);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theWindowReflectsTheVillagersRecipes() {
        villager.setRecipes(List.of(
                recipe(new ItemStack(Material.DIAMOND, 2), new ItemStack(Material.EMERALD, 5)),
                recipe(new ItemStack(Material.BREAD), new ItemStack(Material.WHEAT, 3))));

        Inventory menu = view.beginSession(editor, villager).getInventory();

        assertThat(menu.getItem(VillagerManagerLayout.slot(0, VillagerManagerLayout.BUY_A_COL)))
                .isEqualTo(new ItemStack(Material.EMERALD, 5));
        assertThat(menu.getItem(VillagerManagerLayout.slot(0, VillagerManagerLayout.SELL_COL)))
                .isEqualTo(new ItemStack(Material.DIAMOND, 2));
        assertThat(menu.getItem(VillagerManagerLayout.slot(1, VillagerManagerLayout.BUY_A_COL)))
                .isEqualTo(new ItemStack(Material.WHEAT, 3));
        assertThat(menu.getItem(VillagerManagerLayout.slot(1, VillagerManagerLayout.SELL_COL)))
                .isEqualTo(new ItemStack(Material.BREAD));
    }

    @Test
    void anEditedSellSlotAppliesToTheLiveMerchantAndRoundTripsThroughPdc() {
        villager.setRecipes(List.of(recipe(new ItemStack(Material.DIAMOND, 1), new ItemStack(Material.EMERALD, 4))));
        VillagerManagerHolder holder = view.beginSession(editor, villager);
        holder.getInventory()
                .setItem(
                        VillagerManagerLayout.slot(0, VillagerManagerLayout.SELL_COL),
                        new ItemStack(Material.DIAMOND, 3));

        view.onClose(holder);

        assertThat(villager.getRecipes()).hasSize(1);
        assertThat(villager.getRecipes().get(0).getResult()).isEqualTo(new ItemStack(Material.DIAMOND, 3));
        // The reapply the load listener performs restores the same trades from PDC.
        villager.setRecipes(List.of());
        store.apply(villager);
        assertThat(villager.getRecipes()).hasSize(1);
        assertThat(villager.getRecipes().get(0).getResult()).isEqualTo(new ItemStack(Material.DIAMOND, 3));
        assertThat(villager.getRecipes().get(0).getIngredients()).containsExactly(new ItemStack(Material.EMERALD, 4));
    }

    @Test
    void fillingAnEmptyRowAddsATrade() {
        villager.setRecipes(List.of(recipe(new ItemStack(Material.DIAMOND), new ItemStack(Material.EMERALD))));
        VillagerManagerHolder holder = view.beginSession(editor, villager);
        holder.getInventory()
                .setItem(
                        VillagerManagerLayout.slot(1, VillagerManagerLayout.BUY_A_COL),
                        new ItemStack(Material.EMERALD));
        holder.getInventory()
                .setItem(
                        VillagerManagerLayout.slot(1, VillagerManagerLayout.SELL_COL),
                        new ItemStack(Material.STICK, 8));

        view.onClose(holder);

        assertThat(villager.getRecipes()).hasSize(2);
        assertThat(villager.getRecipes().get(1).getResult()).isEqualTo(new ItemStack(Material.STICK, 8));
    }

    @Test
    void theRemoveButtonDropsATrade() {
        villager.setRecipes(List.of(
                recipe(new ItemStack(Material.DIAMOND), new ItemStack(Material.EMERALD)),
                recipe(new ItemStack(Material.BREAD), new ItemStack(Material.WHEAT))));
        VillagerManagerHolder holder = view.beginSession(editor, villager);

        boolean cancelled = view.handleClick(holder, VillagerManagerLayout.slot(0, VillagerManagerLayout.REMOVE_COL));
        view.onClose(holder);

        assertThat(cancelled).isTrue();
        assertThat(villager.getRecipes()).hasSize(1);
        assertThat(villager.getRecipes().get(0).getResult()).isEqualTo(new ItemStack(Material.BREAD));
    }

    @Test
    void theToggleFlipsTheVillagersDisableFlag() {
        VillagerManagerHolder holder = view.beginSession(editor, villager);
        assertThat(flags.tradesDisabled(villager)).isFalse();

        boolean cancelled = view.handleClick(holder, VillagerManagerLayout.TOGGLE_SLOT);

        assertThat(cancelled).isTrue();
        assertThat(flags.tradesDisabled(villager)).isTrue();
        view.handleClick(holder, VillagerManagerLayout.TOGGLE_SLOT);
        assertThat(flags.tradesDisabled(villager)).isFalse();
    }

    @Test
    void aBuySlotClickIsNotCancelled() {
        VillagerManagerHolder holder = view.beginSession(editor, villager);

        assertThat(view.handleClick(holder, VillagerManagerLayout.slot(0, VillagerManagerLayout.BUY_A_COL)))
                .isFalse();
    }

    private static MerchantRecipe recipe(ItemStack result, ItemStack ingredient) {
        MerchantRecipe recipe = new MerchantRecipe(result, 0, 9, false);
        recipe.addIngredient(ingredient);
        return recipe;
    }

    /** Resolves each key to its own id — enough for the GuiText path the tests do not assert text on. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs every scheduled task inline so the close-time apply happens before the assertions. */
    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
