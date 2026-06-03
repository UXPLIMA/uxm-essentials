package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The source-free usage derivation must reproduce Brigadier's smart-usage suffix without ever touching
 * the live source: it walks the built node's children off {@code getUsageText()} and brackets a deeper
 * branch as optional when its parent is itself executable, exactly the way {@code getSmartUsage} does, but
 * with no {@code canUse} call to trip the root's permission predicate. MockBukkit boots Paper's Brigadier
 * so {@link Commands#literal}/{@link Commands#argument} build real nodes.
 */
class BrigadierUsageTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void requiredThenOptionalForGamemodeShape() {
        LiteralCommandNode<CommandSourceStack> node = Commands.literal("gamemode")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .executes(c -> 1)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(c -> 1)))
                .build();

        assertThat(BrigadierUsage.of(node)).isEqualTo("<mode> [<player>]");
    }

    @Test
    void chainedRequiredThenOptionalForPayShape() {
        LiteralCommandNode<CommandSourceStack> node = Commands.literal("pay")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(c -> 1)
                                .then(Commands.argument("currency", StringArgumentType.word())
                                        .executes(c -> 1))))
                .build();

        assertThat(BrigadierUsage.of(node)).isEqualTo("<player> <amount> [<currency>]");
    }

    @Test
    void emptyForAChildlessNode() {
        LiteralCommandNode<CommandSourceStack> node =
                Commands.literal("afk").executes(c -> 1).build();

        assertThat(BrigadierUsage.of(node)).isEmpty();
    }
}
