package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.BankService;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankMember;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankRole;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /bank create} through its real Brigadier node: the create subcommand takes a
 * name (and optional currency) with no id argument, the system-assigned id reaches {@code BankService.createBank},
 * and the creator is told the id they were given through {@code BANK_CREATED}.
 */
class BankCommandTest {

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef playerRef;

    private BankService bankService;
    private EconomyNotifier notifier;
    private EconomyServices services;
    private Messages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uxmEssentials");
        player = server.addPlayer("Alice");
        player.setOp(true);
        playerRef = new PlayerRef(player.getUniqueId(), "Alice");

        bankService = mock(BankService.class);
        notifier = mock(EconomyNotifier.class);
        messages = mock(Messages.class);

        Scheduler scheduler = mock(Scheduler.class);
        // Run both the off-tick body and the entity-hop reply inline so the test is deterministic.
        doAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(0);
                    runnable.run();
                    return null;
                })
                .when(scheduler)
                .async(any(Runnable.class));
        doAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(1);
                    runnable.run();
                    return null;
                })
                .when(scheduler)
                .onEntity(any(PlayerRef.class), any(Runnable.class));

        services = mock(EconomyServices.class);
        when(services.bankService()).thenReturn(bankService);
        when(services.notifier()).thenReturn(notifier);
        when(services.scheduler()).thenReturn(scheduler);
        when(services.currencies()).thenReturn(CurrencyRegistry.single(COINS));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createTakesANameWithoutAnIdArgument() {
        SharedBank created = bankFor("eEa12523", "Vault");
        when(bankService.createBank(eq("Vault"), eq(COINS), any(PlayerRef.class)))
                .thenReturn(Result.ok(created));

        execute("bank create Vault");

        verify(bankService).createBank(eq("Vault"), eq(COINS), any(PlayerRef.class));
    }

    @Test
    void createReportsTheAssignedId() {
        SharedBank created = bankFor("eEa12523", "Vault");
        when(bankService.createBank(eq("Vault"), eq(COINS), any(PlayerRef.class)))
                .thenReturn(Result.ok(created));

        execute("bank create Vault");

        verify(notifier)
                .send(
                        any(PlayerRef.class),
                        eq(EconomyMessageKey.BANK_CREATED),
                        eq(Map.of("id", "eEa12523", "name", "Vault")));
    }

    private SharedBank bankFor(String id, String name) {
        List<BankMember> members = List.of(new BankMember(playerRef, BankRole.LEADER));
        return new SharedBank(id, name, Money.zero(COINS), playerRef, members, 0L);
    }

    private void execute(String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new BankCommand(plugin, services, messages).build());
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }
}
