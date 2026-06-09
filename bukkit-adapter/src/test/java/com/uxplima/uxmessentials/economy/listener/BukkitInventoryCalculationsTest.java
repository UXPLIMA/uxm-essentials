package com.uxplima.uxmessentials.economy.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.uxplima.uxmessentials.economy.adapter.outbound.BukkitInventoryCalculations;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Denomination;
import com.uxplima.uxmessentials.economy.domain.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

@SuppressWarnings("deprecation")
class BukkitInventoryCalculationsTest {

    private ServerMock server;
    private Currency gems;
    private Denomination emerald;
    private Denomination emeraldBlock;
    private BukkitInventoryCalculations calculations;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        calculations = new BukkitInventoryCalculations();

        // 1 Emerald = 1 gem, 1 Emerald Block = 9 gems
        emerald = new Denomination(BigDecimal.ONE, "EMERALD", "Emerald", null);
        emeraldBlock = new Denomination(BigDecimal.valueOf(9), "EMERALD_BLOCK", "Emerald Block", null);

        gems = mock(Currency.class);
        when(gems.id()).thenReturn(CurrencyId.of("gems"));
        when(gems.denominations()).thenReturn(List.of(emeraldBlock, emerald)); // Largest first for greedy credit
        when(gems.precision()).thenReturn(0);
        when(gems.isPhysical()).thenReturn(true);
        when(gems.normalize(org.mockito.ArgumentMatchers.any(BigDecimal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testGetBalance_CountsCorrectly() {
        PlayerMock player = server.addPlayer("Alice");
        player.getInventory().clear();

        player.getInventory().addItem(new ItemStack(Material.EMERALD, 5));
        player.getInventory().addItem(new ItemStack(Material.EMERALD_BLOCK, 2));

        BigDecimal balance = calculations.getBalance(player, gems);
        // 5 * 1 + 2 * 9 = 23
        assertThat(balance).isEqualByComparingTo(BigDecimal.valueOf(23));
    }

    @Test
    void testCredit_AddsLargestPossibleDenominations() {
        PlayerMock player = server.addPlayer("Alice");
        player.getInventory().clear();

        Money toAdd = Money.of(gems, BigDecimal.valueOf(11));
        calculations.credit(player, toAdd);

        // 11 gems -> 1 Emerald Block (9) + 2 Emeralds (2 * 1)
        assertThat(getAmount(player, Material.EMERALD_BLOCK)).isEqualTo(1);
        assertThat(getAmount(player, Material.EMERALD)).isEqualTo(2);

        BigDecimal balance = calculations.getBalance(player, gems);
        assertThat(balance).isEqualByComparingTo(BigDecimal.valueOf(11));
    }

    @Test
    void testDebit_NoChangeRequired() {
        PlayerMock player = server.addPlayer("Alice");
        player.getInventory().clear();

        player.getInventory().addItem(new ItemStack(Material.EMERALD, 5));
        player.getInventory().addItem(new ItemStack(Material.EMERALD_BLOCK, 2)); // Total 23

        Money toRemove = Money.of(gems, BigDecimal.valueOf(11)); // Can pay 1 Block + 2 Emeralds directly
        calculations.debit(player, toRemove);

        // Inventory should now have 1 Block and 3 Emeralds left
        assertThat(calculations.getBalance(player, gems)).isEqualByComparingTo(BigDecimal.valueOf(12));
    }

    @Test
    void testDebit_RequiresBreakingDownDenomination() {
        PlayerMock player = server.addPlayer("Alice");
        player.getInventory().clear();

        // Player has only 1 Emerald Block (9 gems)
        player.getInventory().addItem(new ItemStack(Material.EMERALD_BLOCK, 1));

        Money toRemove = Money.of(gems, BigDecimal.valueOf(3)); // Remove 3 gems
        calculations.debit(player, toRemove);

        // Should break block (9), take 3 gems, return 6 Emeralds change
        assertThat(getAmount(player, Material.EMERALD_BLOCK)).isEqualTo(0);
        assertThat(getAmount(player, Material.EMERALD)).isEqualTo(6);
        assertThat(calculations.getBalance(player, gems)).isEqualByComparingTo(BigDecimal.valueOf(6));
    }

    @Test
    void testMatches_RespectsCustomModelData() {
        Denomination customDenom = new Denomination(BigDecimal.ONE, "GOLD_NUGGET", "Custom Coin", 12345);

        ItemStack customItem = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = customItem.getItemMeta();
        meta.setCustomModelData(12345);
        customItem.setItemMeta(meta);

        ItemStack normalItem = new ItemStack(Material.GOLD_NUGGET);

        assertThat(BukkitInventoryCalculations.matches(customItem, customDenom)).isTrue();
        assertThat(BukkitInventoryCalculations.matches(normalItem, customDenom)).isFalse();
    }

    private int getAmount(PlayerMock player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }
}
