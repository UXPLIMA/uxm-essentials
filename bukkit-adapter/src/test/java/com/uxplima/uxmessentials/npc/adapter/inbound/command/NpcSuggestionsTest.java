package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;

/**
 * Proves the two tab-completion mechanisms every {@code /npc} subcommand wires its arguments to: the shared
 * NPC-name provider ({@link CommandSuggestions#fromStrings}, fed the renderer's warm name set) completes a
 * {@code name} argument against the current NPC names, and the static enum-word provider completes a choice
 * argument against its fixed value set. The maintainer reported that subcommands suggested nothing and that enum
 * values were not suggested; this pins both back.
 */
class NpcSuggestionsTest {

    private static final List<String> STATE_WORDS = List.of("on_fire", "invisible", "silent");

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aNameArgumentCompletesAgainstTheCurrentNpcNames() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher
                .getRoot()
                .addChild(Commands.literal("npc")
                        .then(Commands.literal("mirror")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(CommandSuggestions.fromStrings(() -> Set.of("guide", "shop", "bank")))
                                        .executes(ctx -> 1)))
                        .build());

        List<String> all = suggest(dispatcher, "npc mirror ");
        assertThat(all).containsExactlyInAnyOrder("guide", "shop", "bank");

        // The partial token filters the names case-insensitively.
        assertThat(suggest(dispatcher, "npc mirror gu")).containsExactly("guide");
    }

    @Test
    void anEnumArgumentCompletesAgainstItsFixedValues() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher
                .getRoot()
                .addChild(Commands.literal("npc")
                        .then(Commands.literal("state")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("state", StringArgumentType.word())
                                                .suggests((ctx, builder) -> suggestWords(builder, STATE_WORDS))
                                                .executes(ctx -> 1))))
                        .build());

        assertThat(suggest(dispatcher, "npc state guide ")).containsExactlyInAnyOrder("on_fire", "invisible", "silent");
        assertThat(suggest(dispatcher, "npc state guide in")).containsExactly("invisible");
    }

    private static CompletableFuture<Suggestions> suggestWords(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder, List<String> words) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (word.startsWith(prefix)) {
                builder.suggest(word);
            }
        }
        return builder.buildFuture();
    }

    private List<String> suggest(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        var parse = dispatcher.parse(input, CommandSourceStackMock.from(server.addPlayer()));
        Suggestions suggestions = dispatcher.getCompletionSuggestions(parse).join();
        return suggestions.getList().stream().map(Suggestion::getText).toList();
    }
}
