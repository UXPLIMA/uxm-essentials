package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.SellAll;
import com.uxplima.uxmessentials.economy.application.SellAllOutcome;
import com.uxplima.uxmessentials.economy.application.SellItem;
import com.uxplima.uxmessentials.economy.application.SellOutcome;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Selling takes the items before it pays for them, and only plain items are sold.
 *
 * <p>Both commands paid first and then removed with {@code removeItem(new ItemStack(material, n))}, ignoring what it
 * could not find. {@code removeItem} matches only a plain stack, and the worth was counted by material, so a player
 * holding diamonds renamed on an anvil was paid for them and kept them: {@code /sell} as often as they liked. uxm-plots
 * found the same shape in its item currency on 2026-09-23. Now the plain items are taken on the player's thread, the
 * sale is asked for what was really taken, and whatever it does not pay for goes back.
 */
class SellTakesBeforeItPaysTest {

    private ServerMock server;
    private PlayerMock player;
    private EconomyServices services;
    private EconomyNotifier notifier;
    private SellItem sellItem;
    private SellAll sellAll;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Ada");
        services = mock(EconomyServices.class);
        notifier = mock(EconomyNotifier.class);
        sellItem = mock(SellItem.class);
        sellAll = mock(SellAll.class);
        Scheduler scheduler = mock(Scheduler.class);
        when(services.notifier()).thenReturn(notifier);
        when(services.scheduler()).thenReturn(scheduler);
        when(services.sellItem()).thenReturn(sellItem);
        when(services.sellAll()).thenReturn(sellAll);
        doAnswer(call -> {
                    call.<Runnable>getArgument(0).run();
                    return null;
                })
                .when(scheduler)
                .async(any(Runnable.class));
        doAnswer(call -> {
                    call.<Runnable>getArgument(1).run();
                    return null;
                })
                .when(scheduler)
                .onEntity(any(PlayerRef.class), any(Runnable.class));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aRenamedStackInHandIsNotSoldAndStays() {
        player.getInventory().setItemInMainHand(renamed(Material.DIAMOND, 10));

        new SellCommand(services, mock(Messages.class)).sell(player, Optional.empty());

        verify(sellItem, never()).sell(any(), anyString(), anyInt());
        verify(notifier).send(any(PlayerRef.class), eq(EconomyMessageKey.SELL_NOT_SELLABLE), anyMap());
        assertThat(count(Material.DIAMOND)).isEqualTo(10);
    }

    @Test
    void aSaleTakesWhatItPaysFor() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 10));
        when(sellItem.sell(any(), eq("diamond"), eq(4)))
                .thenReturn(new SellOutcome(true, Optional.of(mock(Money.class))));

        new SellCommand(services, mock(Messages.class)).sell(player, Optional.of(4));

        assertThat(count(Material.DIAMOND)).isEqualTo(6);
    }

    @Test
    void aRefusedSaleGivesTheItemsBack() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 10));
        when(sellItem.sell(any(), anyString(), anyInt())).thenReturn(new SellOutcome(false, Optional.empty()));

        new SellCommand(services, mock(Messages.class)).sell(player, Optional.empty());

        assertThat(count(Material.DIAMOND)).isEqualTo(10);
    }

    @Test
    void sellAllSellsOnlyPlainStacksAndGivesBackWhatWasNotBought() {
        player.getInventory().addItem(renamed(Material.DIAMOND, 10));
        player.getInventory().addItem(new ItemStack(Material.EMERALD, 5));
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 7));
        SellAllOutcome emeraldsOnly = new SellAllOutcome(
                Map.of("emerald", 5),
                Map.of(mock(com.uxplima.uxmessentials.economy.domain.Currency.class), mock(Money.class)));
        when(sellAll.sellAll(any(), eq(Map.of("emerald", 5, "cobblestone", 7)))).thenReturn(emeraldsOnly);

        new SellAllCommand(services, mock(Messages.class)).sell(player);

        assertThat(count(Material.DIAMOND)).isEqualTo(10);
        assertThat(count(Material.EMERALD)).isZero();
        assertThat(count(Material.COBBLESTONE)).isEqualTo(7);
    }

    private int count(Material material) {
        return player.getInventory().all(material).values().stream()
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    private static ItemStack renamed(Material material, int amount) {
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Shiny"));
        stack.setItemMeta(meta);
        return stack;
    }
}
