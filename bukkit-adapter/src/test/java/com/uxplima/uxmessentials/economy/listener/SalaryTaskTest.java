package com.uxplima.uxmessentials.economy.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.outbound.SalaryTask;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"unchecked", "deprecation"})
class SalaryTaskTest {

    private Plugin plugin;
    private Server server;
    private Scheduler scheduler;
    private EconomyProvider economyProvider;
    private Permissions permissions;
    private EconomyNotifier notifier;
    private Currency coins;
    private Player player;
    private PlayerRef ref;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);

        scheduler = mock(Scheduler.class);
        // Stub scheduler.async and onGlobal to run synchronously in tests
        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(0);
                    task.run();
                    return null;
                })
                .when(scheduler)
                .async(any(Runnable.class));
        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(0);
                    task.run();
                    return null;
                })
                .when(scheduler)
                .onGlobal(any(Runnable.class));

        economyProvider = mock(EconomyProvider.class);
        permissions = mock(Permissions.class);
        notifier = mock(EconomyNotifier.class);
        coins = mock(Currency.class);
        when(coins.id()).thenReturn(CurrencyId.of("coins"));

        player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Bob");

        ref = new PlayerRef(uuid, "Bob");
        when(server.getOnlinePlayers()).thenReturn((Collection) List.of(player));
        when(notifier.amount(any())).thenReturn("$100");
        when(permissions.resolveQuota(any(), any(), any(), eq(100L))).thenReturn(Permissions.QuotaResult.limited(100L));
    }

    @Test
    void whenDisabled_doesNotStartReschedulingLoop() {
        SalaryTask task = new SalaryTask(
                plugin, scheduler, economyProvider, permissions, notifier, coins, false, Duration.ofMinutes(1), 100);

        task.start();

        verify(scheduler, never()).asyncAfter(any(), any());
    }

    @Test
    void whenEnabled_startsReschedulingLoop() {
        SalaryTask task = new SalaryTask(
                plugin, scheduler, economyProvider, permissions, notifier, coins, true, Duration.ofMinutes(1), 100);

        task.start();

        verify(scheduler).asyncAfter(eq(Duration.ofMinutes(1)), any());
    }

    @Test
    void tickCreditsSalaryAndSendsNotification() {
        SalaryTask task = new SalaryTask(
                plugin, scheduler, economyProvider, permissions, notifier, coins, true, Duration.ofMinutes(1), 100);

        when(permissions.resolveQuota(eq(ref), any(), any(), eq(100L)))
                .thenReturn(Permissions.QuotaResult.limited(150L));

        Money expectedSalary = Money.of(coins, BigDecimal.valueOf(150));
        when(economyProvider.credit(eq(ref), eq(expectedSalary))).thenReturn(Result.ok());

        org.mockito.ArgumentCaptor<Runnable> captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);

        task.start();

        verify(scheduler).asyncAfter(eq(Duration.ofMinutes(1)), captor.capture());
        captor.getValue().run();

        verify(economyProvider).ensureAccount(eq(ref), eq(coins));
        verify(economyProvider).credit(eq(ref), eq(expectedSalary));
        verify(notifier).send(eq(ref), eq(EconomyMessageKey.SALARY_PAYOUT), any());
    }

    @Test
    void tickSkipsIfResolvedAmountIsZeroOrNegative() {
        SalaryTask task = new SalaryTask(
                plugin, scheduler, economyProvider, permissions, notifier, coins, true, Duration.ofMinutes(1), 100);

        when(permissions.resolveQuota(eq(ref), any(), any(), eq(100L))).thenReturn(Permissions.QuotaResult.limited(0L));

        org.mockito.ArgumentCaptor<Runnable> captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);

        task.start();

        verify(scheduler).asyncAfter(eq(Duration.ofMinutes(1)), captor.capture());
        captor.getValue().run();

        verify(economyProvider, never()).credit(any(), any());
    }
}
