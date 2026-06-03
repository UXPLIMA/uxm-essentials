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
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /staff}: the online staff roster. It mirrors {@code /list} but narrows the
 * online set to holders of the staff-membership node {@code uxmessentials.staff.member}, and remains
 * vanish-aware through the same {@code canSee} graph. Ordinary players are absent; a vanished staffer the
 * viewer cannot see drops out of both the line and the count; an empty roster resolves the dedicated empty
 * key. The {@link Messages} fake echoes the resolved key with its placeholders so the rendered line is
 * observable through the player's message queue.
 */
class StaffCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String USE_PERMISSION = "uxmessentials.staff.use";
    private static final String STAFF_MEMBER = "uxmessentials.staff.member";

    private ServerMock server;
    private StaffCommand command;

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
        command = new StaffCommand(services, new EchoMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theLiteralIsStaff() {
        assertThat(command.build().getLiteral()).isEqualTo("staff");
    }

    @Test
    void onlyStaffMembersAreListed() {
        PlayerMock alice = server.addPlayer("Alice");
        server.addPlayer("Bob");
        alice.addAttachment(MockBukkit.createMockPlugin(), USE_PERMISSION, true);
        alice.addAttachment(MockBukkit.createMockPlugin(), STAFF_MEMBER, true);

        execute(CommandSourceStackMock.from(alice), "staff");

        String line = PLAIN.serialize(alice.nextComponentMessage());
        assertThat(line).contains("count=1").contains("Alice").doesNotContain("Bob");
    }

    @Test
    void aVanishedStaffMemberTheViewerCannotSeeIsExcluded() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock ghost = server.addPlayer("Ghost");
        alice.addAttachment(MockBukkit.createMockPlugin(), USE_PERMISSION, true);
        alice.addAttachment(MockBukkit.createMockPlugin(), STAFF_MEMBER, true);
        ghost.addAttachment(MockBukkit.createMockPlugin(), STAFF_MEMBER, true);
        alice.hidePlayer(MockBukkit.createMockPlugin(), ghost); // alice's canSee graph drops the vanished ghost

        execute(CommandSourceStackMock.from(alice), "staff");

        String line = PLAIN.serialize(alice.nextComponentMessage());
        assertThat(line).contains("count=1").contains("Alice").doesNotContain("Ghost");
    }

    @Test
    void emptyRosterUsesTheEmptyKey() {
        PlayerMock viewer = server.addPlayer("Viewer");
        viewer.addAttachment(MockBukkit.createMockPlugin(), USE_PERMISSION, true);

        execute(CommandSourceStackMock.from(viewer), "staff");

        // The EchoMessages fake asserts the empty branch resolves PresenceMessageKey.STAFF_EMPTY.
        String line = PLAIN.serialize(viewer.nextComponentMessage());
        assertThat(line).isEqualTo("empty");
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

    /** Echoes the roster placeholders, or a marker for the empty key, so the rendered output is assertable. */
    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            assertThat(key).isIn(PresenceMessageKey.STAFF_LIST, PresenceMessageKey.STAFF_EMPTY);
            if (key == PresenceMessageKey.STAFF_EMPTY) {
                return "empty";
            }
            return "count=" + placeholders.get("count") + " players=" + placeholders.get("players");
        }
    }
}
