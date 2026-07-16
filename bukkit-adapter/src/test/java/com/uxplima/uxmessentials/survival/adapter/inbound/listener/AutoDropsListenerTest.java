package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.application.port.SurvivalSales;
import com.uxplima.uxmessentials.survival.domain.SellPrices;
import com.uxplima.uxmessentials.survival.domain.SmeltMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the composed break-drop pipeline: auto-pickup routes a break's drops into the inventory and
 * overflows the surplus to the ground, auto-smelt yields the smelted item and composes with pickup, and auto-sell
 * credits the wallet at the configured price — and stays inert with no economy provider.
 */
class AutoDropsListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private PdcSurvivalToggles toggles;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        player.teleport(new Location(world, 0.5, 65, 0.5));
        player.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.survival.autopickup", true);
        player.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.survival.autosmelt", true);
        player.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.survival.autosell", true);
        toggles = new PdcSurvivalToggles();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void autoPickupRoutesDropsIntoTheInventory() {
        Block ore = blockWithDrops(Material.COAL_ORE, new ItemStack(Material.COAL, 3));
        AutoDropsListener listener = pickupOnly();

        listener.onBreak(new BlockBreakEvent(ore, player));

        assertThat(player.getInventory().contains(Material.COAL, 3)).isTrue();
        assertThat(groundItems()).isEmpty();
    }

    @Test
    void autoPickupOverflowsToTheGroundWhenTheInventoryIsFull() {
        fillInventory(Material.STONE);
        Block ore = blockWithDrops(Material.COAL_ORE, new ItemStack(Material.COAL, 5));
        AutoDropsListener listener = pickupOnly();

        listener.onBreak(new BlockBreakEvent(ore, player));

        // Every slot is a full stack of a different material, so the coal cannot fit and spills to the ground.
        assertThat(groundItems()).hasSize(1);
        assertThat(groundItems().get(0).getType()).isEqualTo(Material.COAL);
        assertThat(groundItems().get(0).getAmount()).isEqualTo(5);
    }

    @Test
    void autoSmeltYieldsTheSmeltedOutputAndComposesWithPickup() {
        Block ore = blockWithDrops(Material.IRON_ORE, new ItemStack(Material.RAW_IRON, 2));
        AutoDropsListener listener = new AutoDropsListener(
                true,
                false,
                true,
                new SmeltMap(Map.of("RAW_IRON", "IRON_INGOT")),
                false,
                new SellPrices(Map.of()),
                Optional.empty(),
                toggles);

        listener.onBreak(new BlockBreakEvent(ore, player));

        // Smelt runs before pickup: the ingot lands in the inventory and no raw iron survives.
        assertThat(player.getInventory().contains(Material.IRON_INGOT, 2)).isTrue();
        assertThat(player.getInventory().contains(Material.RAW_IRON)).isFalse();
    }

    @Test
    void autoSmeltDropsTheSmeltedOutputOnTheGroundWithoutPickup() {
        Block ore = blockWithDrops(Material.IRON_ORE, new ItemStack(Material.RAW_IRON, 1));
        AutoDropsListener listener = new AutoDropsListener(
                false,
                false,
                true,
                new SmeltMap(Map.of("RAW_IRON", "IRON_INGOT")),
                false,
                new SellPrices(Map.of()),
                Optional.empty(),
                toggles);

        listener.onBreak(new BlockBreakEvent(ore, player));

        assertThat(groundItems()).hasSize(1);
        assertThat(groundItems().get(0).getType()).isEqualTo(Material.IRON_INGOT);
    }

    @Test
    void autoSellCreditsTheWalletAtTheConfiguredPrice() {
        Block ore = blockWithDrops(Material.DIAMOND_ORE, new ItemStack(Material.DIAMOND, 2));
        RecordingSales sales = new RecordingSales(true);
        AutoDropsListener listener = new AutoDropsListener(
                true,
                false,
                false,
                new SmeltMap(Map.of()),
                true,
                new SellPrices(Map.of("DIAMOND", new BigDecimal("300"))),
                Optional.of(sales),
                toggles);

        listener.onBreak(new BlockBreakEvent(ore, player));

        // Two diamonds at 300 each: 600 credited, and the sold stack never reaches the inventory or the ground.
        assertThat(sales.credited).isEqualTo(new BigDecimal("600"));
        assertThat(player.getInventory().contains(Material.DIAMOND)).isFalse();
        assertThat(groundItems()).isEmpty();
    }

    @Test
    void autoSellIsInertWithoutAnEconomyProvider() {
        Block ore = blockWithDrops(Material.DIAMOND_ORE, new ItemStack(Material.DIAMOND, 2));
        AutoDropsListener listener = new AutoDropsListener(
                true,
                false,
                false,
                new SmeltMap(Map.of()),
                true,
                new SellPrices(Map.of("DIAMOND", new BigDecimal("300"))),
                Optional.empty(),
                toggles);

        listener.onBreak(new BlockBreakEvent(ore, player));

        // No provider: the sell stage never fires, so auto-pickup simply keeps the diamonds.
        assertThat(player.getInventory().contains(Material.DIAMOND, 2)).isTrue();
    }

    // --- helpers -----------------------------------------------------------------------------------------------------

    private AutoDropsListener pickupOnly() {
        return new AutoDropsListener(
                true, false, false, new SmeltMap(Map.of()), false, new SellPrices(Map.of()), Optional.empty(), toggles);
    }

    private Block blockWithDrops(Material type, ItemStack... drops) {
        BlockMock block = world.getBlockAt(0, 64, 0);
        block.setType(type);
        block.setDrops(List.of(drops));
        return block;
    }

    private void fillInventory(Material filler) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(filler, 64));
        }
    }

    private List<ItemStack> groundItems() {
        List<ItemStack> items = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Item item) {
                items.add(item.getItemStack());
            }
        }
        return items;
    }

    /** A fake economy seam that records the total credited and reports a fixed success. */
    private static final class RecordingSales implements SurvivalSales {
        private final boolean success;
        private BigDecimal credited = BigDecimal.ZERO;

        RecordingSales(boolean success) {
            this.success = success;
        }

        @Override
        public boolean credit(PlayerRef who, BigDecimal amount) {
            if (success) {
                credited = credited.add(amount);
            }
            return success;
        }
    }
}
