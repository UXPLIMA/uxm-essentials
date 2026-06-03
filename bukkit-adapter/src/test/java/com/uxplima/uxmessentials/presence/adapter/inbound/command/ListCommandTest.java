package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.application.ClearAfkOnActivity;
import com.uxplima.uxmessentials.presence.application.ClearNick;
import com.uxplima.uxmessentials.presence.application.MarkAfk;
import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.presence.application.ResolveVisibility;
import com.uxplima.uxmessentials.presence.application.SetNick;
import com.uxplima.uxmessentials.presence.application.ToggleVanish;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /list}: the read-only online-player listing, vanish-aware through the same
 * {@code canSee} graph the presence {@code VisibilityApplier} drives. A viewing player sees only the players they
 * can see, with the count matching; a vanished player hidden from the viewer drops out of both the line and the
 * count; the console (no {@code canSee} graph) sees everyone. The {@link Messages} fake echoes the resolved key
 * with its placeholders substituted so the rendered line is observable through the player's message queue.
 */
class ListCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String PERMISSION = "uxmessentials.list.use";

    private ServerMock server;
    private ListCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        PresenceServices services = new PresenceServices(
                mock(MarkAfk.class),
                mock(ClearAfkOnActivity.class),
                mock(ToggleVanish.class),
                mock(ResolveVisibility.class),
                mock(SetNick.class),
                mock(ClearNick.class));
        command = new ListCommand(services, new EchoMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theLiteralIsList() {
        assertThat(command.build().getLiteral()).isEqualTo("list");
    }

    @Test
    void aViewerSeesEveryOnlinePlayerTheyCanSee() {
        PlayerMock alice = server.addPlayer("Alice");
        server.addPlayer("Bob");
        alice.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);

        execute(CommandSourceStackMock.from(alice), "list");

        String line = PLAIN.serialize(alice.nextComponentMessage());
        assertThat(line).contains("count=2").contains("Alice").contains("Bob");
    }

    @Test
    void aVanishedPlayerTheViewerCannotSeeIsExcluded() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock ghost = server.addPlayer("Ghost");
        alice.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);
        alice.hidePlayer(MockBukkit.createMockPlugin(), ghost); // alice's canSee graph drops the vanished ghost

        execute(CommandSourceStackMock.from(alice), "list");

        String line = PLAIN.serialize(alice.nextComponentMessage());
        assertThat(line).contains("count=1").contains("Alice").doesNotContain("Ghost");
    }

    @Test
    void theConsoleSeesEveryOnlinePlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock ghost = server.addPlayer("Ghost");
        alice.hidePlayer(MockBukkit.createMockPlugin(), ghost); // hidden from alice, but the console has no graph
        ConsoleCommandSenderMock console = server.getConsoleSender();

        execute(CommandSourceStackMock.from(console), "list");

        String line = PLAIN.serialize(console.nextComponentMessage());
        assertThat(line).contains("count=2").contains("Alice").contains("Ghost");
    }

    private void execute(CommandSourceStack source, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        try {
            dispatcher.execute(input, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    /** Echoes the key's placeholders as a single MiniMessage-safe line so the rendered output is assertable. */
    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            assertThat(key).isEqualTo(PresenceMessageKey.LIST_PLAYERS);
            return "count=" + placeholders.get("count") + " players=" + placeholders.get("players");
        }
    }
}
