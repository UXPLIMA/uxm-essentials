package com.uxplima.uxmessentials.presence.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.adapter.inbound.command.GcCommand;
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
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /gc}: a server-health read-out (TPS, uptime, memory, loaded chunks and
 * entities). A pure read with no use case and no state mutation. The {@link Messages} fake echoes the resolved
 * key and its placeholders so the rendered reply — and that every health field is present — is observable
 * through the sender's message queue.
 */
class GcCommandPathTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String PERMISSION = "uxmessentials.gc.use";

    private ServerMock server;
    private GcCommand command;

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
        command = new GcCommand(services, new EchoMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theLiteralIsGc() {
        assertThat(command.build().getLiteral()).isEqualTo("gc");
    }

    @Test
    void theAliasesCoverLagTpsAndMem() {
        assertThat(command.aliases()).contains("lag", "tps", "mem");
    }

    @Test
    void reportsTheServerHealthFields() {
        PlayerMock viewer = server.addPlayer("Viewer");
        viewer.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);

        execute(CommandSourceStackMock.from(viewer), "gc");

        String line = PLAIN.serialize(viewer.nextComponentMessage());
        assertThat(line)
                .contains("gc-result")
                .contains("tps1m=")
                .contains("memUsed=")
                .contains("memMax=")
                .contains("chunks=")
                .contains("entities=")
                .contains("hours=")
                .contains("minutes=")
                .contains("seconds=");
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
            String prefix = key == PresenceMessageKey.GC_RESULT ? "gc-result" : "other";
            StringBuilder out = new StringBuilder(prefix);
            placeholders.forEach(
                    (name, value) -> out.append(' ').append(name).append('=').append(value));
            return out.toString();
        }
    }
}
