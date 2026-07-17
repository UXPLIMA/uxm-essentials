package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.application.ClearAfkOnActivity;
import com.uxplima.uxmessentials.presence.application.ClearNick;
import com.uxplima.uxmessentials.presence.application.MarkAfk;
import com.uxplima.uxmessentials.presence.application.SetNick;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /nick} Brigadier surface after the discoverability rework. The added
 * {@code clear}/{@code off} literal subcommands clear the sender's own nick, the long-standing {@code /nick off}
 * keyword still clears it, {@code /nick <name>} still sets the sender's own, and {@code /nick <player> <nick>}
 * still sets another player's. The first-argument completion offers the sender's own account name (item 38) plus
 * the online roster and the {@code off}/{@code clear} keywords. The {@code SetNick}/{@code ClearNick} use cases
 * are Mockito mocks so each path is asserted by the call it makes rather than by the rendered confirmation.
 */
class NickCommandTest {

    private ServerMock server;
    private SetNick setNick;
    private ClearNick clearNick;
    private NickCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        setNick = mock(SetNick.class);
        clearNick = mock(ClearNick.class);
        PresenceServices services =
                new PresenceServices(mock(MarkAfk.class), mock(ClearAfkOnActivity.class), setNick, clearNick);
        command = new NickCommand(services, new SilentMessages(), new InlineScheduler());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theLiteralIsNick() {
        assertThat(command.build().getLiteral()).isEqualTo("nick");
    }

    @Test
    void clearSubcommandClearsTheSendersOwnNick() {
        PlayerMock alice = addPlayer("Alice");

        execute(alice, "nick clear");

        verify(clearNick).clear(ref(alice));
        verifyNoInteractions(setNick);
    }

    @Test
    void offSubcommandClearsTheSendersOwnNick() {
        PlayerMock alice = addPlayer("Alice");

        execute(alice, "nick off");

        verify(clearNick).clear(ref(alice));
        verifyNoInteractions(setNick);
    }

    @Test
    void settingAWordSetsTheSendersOwnNick() {
        PlayerMock alice = addPlayer("Alice");

        execute(alice, "nick Bob");

        verify(setNick).set(ref(alice), ref(alice), "Bob");
        verifyNoInteractions(clearNick);
    }

    @Test
    void theTwoArgumentFormSetsAnotherPlayersNickWhenGated() {
        PlayerMock alice = addPlayer("Alice");
        alice.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.nick.others", true);
        PlayerMock bob = addPlayer("Bob");

        execute(alice, "nick Bob Shadow");

        verify(setNick).set(ref(alice), ref(bob), "Shadow");
    }

    @Test
    void theTwoArgumentFormIsRejectedWithoutTheOthersNode() {
        PlayerMock alice = addPlayer("Alice"); // only the base nick.use node
        addPlayer("Bob");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> executeRaw(alice, "nick Bob Shadow"))
                .isInstanceOf(CommandSyntaxException.class);
        verifyNoInteractions(setNick);
    }

    @Test
    void theFirstArgumentSuggestsTheSendersOwnNameOnlinePlayersAndTheClearKeywords() {
        PlayerMock siracozmen = addPlayer("SIRACOZMEN");
        addPlayer("Bob");

        List<String> suggestions = suggest(siracozmen, "nick ");

        assertThat(suggestions).contains("SIRACOZMEN", "Bob", "off", "clear");
    }

    @Test
    void theFirstArgumentSuggestionsArePrefixFiltered() {
        PlayerMock siracozmen = addPlayer("SIRACOZMEN");
        addPlayer("Bob");

        assertThat(suggest(siracozmen, "nick SIR")).containsExactly("SIRACOZMEN");
        assertThat(suggest(siracozmen, "nick of")).containsExactly("off");
    }

    private PlayerMock addPlayer(String name) {
        PlayerMock player = server.addPlayer(name);
        player.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.nick.use", true);
        return player;
    }

    private void execute(PlayerMock sender, String input) {
        try {
            executeRaw(sender, input);
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private void executeRaw(PlayerMock sender, String input) throws CommandSyntaxException {
        dispatcher().execute(input, CommandSourceStackMock.from(sender));
    }

    private List<String> suggest(PlayerMock sender, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        var parse = dispatcher.parse(input, CommandSourceStackMock.from(sender));
        Suggestions suggestions = dispatcher.getCompletionSuggestions(parse).join();
        return suggestions.getList().stream().map(Suggestion::getText).toList();
    }

    private CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        return dispatcher;
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** The catalog is unused on these paths (confirmations come from the mocked use cases); resolve to the key. */
    private static final class SilentMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, java.util.Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
