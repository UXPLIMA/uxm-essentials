package com.uxplima.uxmessentials.presence.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.adapter.inbound.command.RealnameCommand;
import com.uxplima.uxmessentials.presence.application.ClearAfkOnActivity;
import com.uxplima.uxmessentials.presence.application.ClearNick;
import com.uxplima.uxmessentials.presence.application.MarkAfk;
import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.presence.application.ResolveVisibility;
import com.uxplima.uxmessentials.presence.application.SetNick;
import com.uxplima.uxmessentials.presence.application.ToggleVanish;
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
 * MockBukkit coverage of {@code /realname}: resolve an online player's display name to the underlying account
 * name, vanish-aware through the same {@code canSee} graph {@code /list} reads. A custom display name matches and
 * the reply names the real account; a player the viewer cannot see is unresolvable; an empty/unmatched query
 * yields the not-found reply. The {@link Messages} fake echoes the resolved key and its placeholders so the
 * rendered reply is observable through the sender's message queue.
 */
class RealnameCommandPathTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String PERMISSION = "uxmessentials.realname.use";

    private ServerMock server;
    private RealnameCommand command;

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
        command = new RealnameCommand(services, new EchoMessages(), new InlineScheduler());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theLiteralIsRealname() {
        assertThat(command.build().getLiteral()).isEqualTo("realname");
    }

    @Test
    void aDisplayNameResolvesToTheRealAccountName() {
        PlayerMock sira = server.addPlayer("siracozmen");
        sira.displayName(Component.text("Sir"));
        PlayerMock viewer = server.addPlayer("Viewer");
        viewer.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);

        execute(CommandSourceStackMock.from(viewer), "realname Sir");

        String line = PLAIN.serialize(viewer.nextComponentMessage());
        assertThat(line).contains("result").contains("name=siracozmen").contains("display=Sir");
    }

    @Test
    void aVanishedPlayerTheViewerCannotSeeIsNotResolved() {
        PlayerMock ghost = server.addPlayer("ghostaccount");
        ghost.displayName(Component.text("Ghost"));
        PlayerMock viewer = server.addPlayer("Viewer");
        viewer.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);
        viewer.hidePlayer(MockBukkit.createMockPlugin(), ghost); // viewer's canSee graph drops the vanished ghost

        execute(CommandSourceStackMock.from(viewer), "realname Ghost");

        String line = PLAIN.serialize(viewer.nextComponentMessage());
        assertThat(line).contains("not-found").contains("query=Ghost").doesNotContain("ghostaccount");
    }

    @Test
    void anUnmatchedQueryYieldsNotFound() {
        PlayerMock viewer = server.addPlayer("Viewer");
        viewer.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);

        execute(CommandSourceStackMock.from(viewer), "realname Nobody");

        String line = PLAIN.serialize(viewer.nextComponentMessage());
        assertThat(line).contains("not-found").contains("query=Nobody");
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

    /** Echoes the key and its placeholders as a single MiniMessage-safe line so the rendered reply is assertable. */
    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            String suffix = key == PresenceMessageKey.REALNAME_RESULT ? "result" : "not-found";
            StringBuilder out = new StringBuilder(suffix);
            placeholders.forEach(
                    (name, value) -> out.append(' ').append(name).append('=').append(value));
            return out.toString();
        }
    }

    /** Runs the global-thread roster scan and the sender-thread reply inline so the rendered line is observable. */
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
