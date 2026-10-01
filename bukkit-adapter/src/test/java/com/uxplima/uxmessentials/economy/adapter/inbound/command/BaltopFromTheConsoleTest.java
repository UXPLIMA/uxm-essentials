package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.BalTop;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;

/**
 * The console reads the leaderboard as lines.
 *
 * <p>{@code /baltop} only opened a window, so the console was told the command is for players, though the leaderboard
 * has always had a text form: the {@link BalTop} lines were written and wired, and nothing sent them.
 */
class BaltopFromTheConsoleTest {

    private ServerMock server;
    private EconomyServices services;
    private BalTop balTop;
    private Currency coins;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        coins = mock(Currency.class);
        when(coins.id()).thenReturn(CurrencyId.of("coins"));
        EconomyProvider provider = mock(EconomyProvider.class);
        when(provider.currencies()).thenReturn(Set.of(coins));
        balTop = mock(BalTop.class);
        Scheduler inline = mock(Scheduler.class);
        org.mockito.Mockito.doAnswer(call -> {
                    call.<Runnable>getArgument(0).run();
                    return null;
                })
                .when(inline)
                .async(any());
        services = mock(EconomyServices.class);
        when(services.provider()).thenReturn(provider);
        when(services.scheduler()).thenReturn(inline);
        when(services.balTop()).thenReturn(balTop);
        CurrencyRegistry registry = CurrencyRegistry.single(coins);
        when(services.currencies()).thenReturn(registry);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("/baltop and /baltop 2 from the console print the page as lines")
    void theConsoleReadsTheLines() throws Exception {
        execute("baltop");
        execute("baltop 2");

        verify(balTop).show(any(PlayerRef.class), eq(coins), eq(1));
        verify(balTop).show(any(PlayerRef.class), eq(coins), eq(2));
    }

    private void execute(String input) throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new BaltopCommand(services, mock(Messages.class)).build());
        dispatcher.execute(input, CommandSourceStackMock.from(server.getConsoleSender()));
    }
}
