package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Set;

import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.BanknoteStore;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;

/**
 * The two answers the bulk eco verbs owed the operator.
 *
 * <p>A bare {@code /eco resetall} had no executor at all, so Brigadier answered with its own parse error and
 * {@code eco.admin.resetall-confirm}, the line that says to add {@code --confirm}, shipped in twelve
 * languages unsent. A bulk verb on an empty server ran over nobody and reported nothing, and
 * {@code eco.admin.no-targets} was written for exactly that.
 */
class EcoBulkVerbsTest {

    private ServerMock server;
    private Plugin plugin;
    private EconomyServices services;
    private EconomyNotifier notifier;
    private Messages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uxmEssentials");
        notifier = mock(EconomyNotifier.class);
        messages = mock(Messages.class);
        EconomyProvider provider = mock(EconomyProvider.class);
        Currency coins = mock(Currency.class);
        when(coins.id()).thenReturn(CurrencyId.of("coins"));
        when(coins.normalize(any(BigDecimal.class))).thenAnswer(call -> call.getArgument(0));
        // The amount parser clamps against the currency's own bounds before anything else reads it.
        when(coins.min()).thenReturn(BigDecimal.ZERO);
        when(coins.max()).thenReturn(new BigDecimal("1000000"));
        when(provider.currencies()).thenReturn(Set.of(coins));
        services = mock(EconomyServices.class);
        when(services.provider()).thenReturn(provider);
        when(services.notifier()).thenReturn(notifier);
        when(services.banknoteStore()).thenReturn(mock(BanknoteStore.class));
        when(services.scheduler()).thenReturn(mock(Scheduler.class));
        // Built before the stubbing call: CurrencyRegistry.single touches the mock, and Mockito reads a mock
        // call inside when(...) as an unfinished stubbing.
        CurrencyRegistry registry = CurrencyRegistry.single(coins);
        when(services.currencies()).thenReturn(registry);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aBareResetAllAsksForTheConfirmFlagRatherThanFailingToParse() {
        execute("eco resetall");

        verify(notifier).send(any(PlayerRef.class), eq(EconomyMessageKey.ECO_ADMIN_RESETALL_CONFIRM), any());
    }

    @Test
    void aBulkGiveWithNobodyOnlineSaysSoAndTouchesNothing() {
        execute("eco giveall 100");

        verify(notifier).send(any(PlayerRef.class), eq(EconomyMessageKey.ECO_ADMIN_NO_TARGETS), any());
        verify(services, never()).ecoAdmin();
    }

    @Test
    void aResetAllWithNobodyOnlineSaysSoToo() {
        execute("eco resetall --confirm");

        verify(notifier).send(any(PlayerRef.class), eq(EconomyMessageKey.ECO_ADMIN_NO_TARGETS), any());
        verify(services, never()).ecoAdmin();
    }

    /** Run {@code input} from the console, so the roster this test cares about really is empty. */
    private void execute(String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new EcoCommand(plugin, services, messages).build());
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(server.getConsoleSender()));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }
}
