package com.uxplima.uxmessentials.economy.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.uxplima.uxmessentials.economy.adapter.EconomyConfig;
import com.uxplima.uxmessentials.economy.adapter.inbound.listener.CommandCostListener;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandCostListenerTest {

    private EconomyProvider economy;
    private EconomyNotifier notifier;
    private Currency coins;
    private Player player;
    private PlayerRef alice;

    @BeforeEach
    void setUp() {
        economy = mock(EconomyProvider.class);
        notifier = mock(EconomyNotifier.class);
        coins = mock(Currency.class);
        when(coins.id()).thenReturn(CurrencyId.of("coins"));

        player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alice");
        when(player.hasPermission("uxmessentials.economy.bypasscmdcost")).thenReturn(false);

        alice = new PlayerRef(uuid, "Alice");
        when(economy.currencies()).thenReturn(java.util.Set.of(coins));
        when(notifier.amount(any())).thenReturn("$10");
    }

    @Test
    void whenCommandHasNoCost_doesNothing() {
        CommandCostListener listener = new CommandCostListener(economy, Map.of(), coins, notifier);

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/help", null);
        listener.onCommand(event);

        verify(economy, never()).debit(any(), any());
    }

    @Test
    void whenPlayerHasBypassPermission_doesNotCharge() {
        when(player.hasPermission("uxmessentials.economy.bypasscmdcost")).thenReturn(true);

        EconomyConfig.CommandCost cost = new EconomyConfig.CommandCost(BigDecimal.TEN, "coins");
        CommandCostListener listener = new CommandCostListener(economy, Map.of("fly", cost), coins, notifier);

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/fly", null);
        listener.onCommand(event);

        verify(economy, never()).debit(any(), any());
    }

    @Test
    void whenFundsAreAvailable_chargesPlayerAndSendsMessage() {
        EconomyConfig.CommandCost cost = new EconomyConfig.CommandCost(BigDecimal.TEN, "coins");
        CommandCostListener listener = new CommandCostListener(economy, Map.of("fly", cost), coins, notifier);

        Money charge = Money.of(coins, BigDecimal.TEN);
        when(economy.debit(eq(alice), eq(charge))).thenReturn(Result.ok());

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/fly speed 10", null);
        listener.onCommand(event);

        verify(economy).debit(eq(alice), eq(charge));
        verify(notifier).send(eq(alice), eq(EconomyMessageKey.COMMAND_COST_CHARGED), any());
    }

    @Test
    void whenFundsAreInsufficient_cancelsEventAndSendsInsufficientMessage() {
        EconomyConfig.CommandCost cost = new EconomyConfig.CommandCost(BigDecimal.TEN, "coins");
        CommandCostListener listener = new CommandCostListener(economy, Map.of("fly", cost), coins, notifier);

        Money charge = Money.of(coins, BigDecimal.TEN);
        when(economy.debit(eq(alice), eq(charge))).thenReturn(Result.err(TransferError.INSUFFICIENT_FUNDS));

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/fly", null);
        listener.onCommand(event);

        verify(economy).debit(eq(alice), eq(charge));
        verify(notifier).send(eq(alice), eq(EconomyMessageKey.COMMAND_COST_INSUFFICIENT), any());
        org.junit.jupiter.api.Assertions.assertTrue(event.isCancelled());
    }
}
