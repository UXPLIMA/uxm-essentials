package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.BanknoteStore;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class WithdrawCommandTest {

    private ServerMock server;
    private Plugin plugin;
    private EconomyServices services;
    private EconomyProvider provider;
    private EconomyNotifier notifier;
    private BanknoteStore banknoteStore;
    private Scheduler scheduler;
    private Messages messages;
    private Currency virtualCoins;
    private Currency physicalGems;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uxmEssentials");

        provider = mock(EconomyProvider.class);
        notifier = mock(EconomyNotifier.class);
        banknoteStore = mock(BanknoteStore.class);
        scheduler = mock(Scheduler.class);
        messages = mock(Messages.class);

        services = mock(EconomyServices.class);
        when(services.provider()).thenReturn(provider);
        when(services.notifier()).thenReturn(notifier);
        when(services.banknoteStore()).thenReturn(banknoteStore);
        when(services.scheduler()).thenReturn(scheduler);

        virtualCoins = mock(Currency.class);
        when(virtualCoins.id()).thenReturn(CurrencyId.of("coins"));
        when(virtualCoins.isPhysical()).thenReturn(false);
        when(virtualCoins.normalize(any(BigDecimal.class))).thenAnswer(inv -> inv.getArgument(0));

        physicalGems = mock(Currency.class);
        when(physicalGems.id()).thenReturn(CurrencyId.of("gems"));
        when(physicalGems.isPhysical()).thenReturn(true);
        when(physicalGems.normalize(any(BigDecimal.class))).thenAnswer(inv -> inv.getArgument(0));

        when(provider.currencies()).thenReturn(Set.of(virtualCoins, physicalGems));

        // Stub scheduler execution to run synchronously
        doAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(1);
                    runnable.run();
                    return null;
                })
                .when(scheduler)
                .onEntity(any(PlayerRef.class), any(Runnable.class));

        when(notifier.amount(any())).thenReturn("10");
        // The banknote item name/lore resolve through the catalog; return a parseable MiniMessage string.
        when(messages.resolve(any(PlayerRef.class), any(), any())).thenReturn("<gray>banknote</gray>");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void whenWithdrawPhysicalCurrency_isBlocked() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerRef owner = new PlayerRef(player.getUniqueId(), "Alice");
        Money amount = Money.of(physicalGems, BigDecimal.TEN);

        WithdrawCommand command = new WithdrawCommand(plugin, services, messages);
        command.withdraw(player, owner, amount);

        // Verify physical-not-allowed message is sent
        verify(notifier).send(eq(owner), eq(EconomyMessageKey.WITHDRAW_PHYSICAL_NOT_ALLOWED));
        // Verify no debit occurred
        verify(provider, never()).debit(any(), any());
        // Verify no banknote registered
        verify(banknoteStore, never()).register(any());
        // Verify inventory remains empty (no banknote item added)
        assertThat(player.getInventory().contains(Material.PAPER)).isFalse();
    }

    @Test
    void whenWithdrawVirtualCurrency_succeeds() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerRef owner = new PlayerRef(player.getUniqueId(), "Alice");
        Money amount = Money.of(virtualCoins, BigDecimal.TEN);

        when(provider.debit(eq(owner), eq(amount))).thenReturn(Result.ok());

        WithdrawCommand command = new WithdrawCommand(plugin, services, messages);
        command.withdraw(player, owner, amount);

        // Verify debited from provider
        verify(provider).debit(eq(owner), eq(amount));
        // Verify banknote registered
        verify(banknoteStore).register(any());
        // Verify banknote item (PAPER) added to player's inventory
        assertThat(player.getInventory().contains(Material.PAPER)).isTrue();
        // Verify success notification sent
        verify(notifier).send(eq(owner), eq(EconomyMessageKey.BANKNOTE_WITHDRAWN), any());
    }

    @Test
    void whenWithdrawVirtualCurrency_insufficientFunds_fails() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerRef owner = new PlayerRef(player.getUniqueId(), "Alice");
        Money amount = Money.of(virtualCoins, BigDecimal.TEN);

        when(provider.debit(eq(owner), eq(amount))).thenReturn(Result.err(TransferError.INSUFFICIENT_FUNDS));

        WithdrawCommand command = new WithdrawCommand(plugin, services, messages);
        command.withdraw(player, owner, amount);

        // Verify debit was attempted
        verify(provider).debit(eq(owner), eq(amount));
        // Verify insufficient funds notification sent
        verify(notifier).send(eq(owner), eq(EconomyMessageKey.WITHDRAW_INSUFFICIENT), any());
        // Verify no banknote registered
        verify(banknoteStore, never()).register(any());
        // Verify no banknote item added to player's inventory
        assertThat(player.getInventory().contains(Material.PAPER)).isFalse();
    }
}
