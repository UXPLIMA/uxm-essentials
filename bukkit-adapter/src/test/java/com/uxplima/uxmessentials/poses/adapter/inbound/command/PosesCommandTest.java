package com.uxplima.uxmessentials.poses.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import com.uxplima.uxmessentials.poses.adapter.inbound.gui.PosesSettingsView;
import com.uxplima.uxmessentials.poses.application.TogglePlayerSit;
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
 * Pins the /poses root visibility gate: the root now carries a {@code requires}, so a player with no poses access
 * neither sees nor runs the command, while a player holding either self-service node (the GUI or the toggle) keeps
 * it - the toggle-only player must not lose the command just because they lack the GUI node.
 */
class PosesCommandTest {

    private static final String GUI = "uxmessentials.poses.gui";
    private static final String TOGGLE = "uxmessentials.poses.toggle";

    private ServerMock server;
    private PosesCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        command = new PosesCommand(mock(TogglePlayerSit.class), mock(PosesSettingsView.class), new KeyMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theRootIsHiddenFromAPlayerWithNoPosesAccess() {
        PlayerMock plain = server.addPlayer("Plain");
        plain.addAttachment(MockBukkit.createMockPlugin(), GUI, false);
        plain.addAttachment(MockBukkit.createMockPlugin(), TOGGLE, false);

        assertThat(command.build().getRequirement().test(CommandSourceStackMock.from(plain)))
                .isFalse();
    }

    @Test
    void theRootStaysVisibleForAToggleOnlyPlayer() {
        PlayerMock toggleOnly = server.addPlayer("ToggleOnly");
        toggleOnly.addAttachment(MockBukkit.createMockPlugin(), GUI, false);
        toggleOnly.addAttachment(MockBukkit.createMockPlugin(), TOGGLE, true);

        assertThat(command.build().getRequirement().test(CommandSourceStackMock.from(toggleOnly)))
                .isTrue();
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
