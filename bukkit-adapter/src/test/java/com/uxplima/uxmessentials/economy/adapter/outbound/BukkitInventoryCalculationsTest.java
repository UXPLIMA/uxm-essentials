package com.uxplima.uxmessentials.economy.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

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

/** A delivery of currency items fills what the inventory has room for and drops nothing on the ground. */
class BukkitInventoryCalculationsTest {

    private static final Currency GOLD = Currency.builder(CurrencyId.of("gold"))
            .physical(true)
            .denominations(List.of(new Denomination(BigDecimal.ONE, "GOLD_NUGGET", "gold nugget", null)))
            .build();

    private ServerMock server;
    private PlayerMock alex;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        alex = server.addPlayer("Alex");
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void whatDoesNotFitStaysOutAndNothingIsDropped() {
        // Every slot but the first is full; the test server also puts items in the armour slots, so those are too.
        ItemStack[] contents = alex.getInventory().getContents();
        for (int slot = 1; slot < contents.length; slot++) {
            contents[slot] = new ItemStack(Material.STONE, 64);
        }
        alex.getInventory().setContents(contents);
        BukkitInventoryCalculations calculations = new BukkitInventoryCalculations();

        BigDecimal fit = calculations.creditFitting(alex, Money.of(GOLD, new BigDecimal("100")));

        assertThat(fit).isEqualByComparingTo("64");
        assertThat(calculations.getBalance(alex, GOLD)).isEqualByComparingTo("64");
        assertThat(alex.getWorld().getEntitiesByClass(Item.class)).isEmpty();
    }
}
