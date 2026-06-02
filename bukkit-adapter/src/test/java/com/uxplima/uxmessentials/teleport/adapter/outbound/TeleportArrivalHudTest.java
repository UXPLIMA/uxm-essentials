package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the arrival HUD's gating: it resolves and shows the title only for an announcing kind, for an online
 * player, when the toggle is on. The resolved-key record is the observable proxy for "a title would show" —
 * MockBukkit does not round-trip the Adventure {@code showTitle}, so the show itself is smoke-tested for no
 * throw (uxmLib's own {@code TitlesTest} covers the title helper).
 */
class TeleportArrivalHudTest {

    private ServerMock server;
    private RecordingMessages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        messages = new RecordingMessages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TeleportArrivalHud hud(boolean enabled) {
        return new TeleportArrivalHud(messages, server, () -> enabled);
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    @Test
    void showsTheArrivalTitleForAnAnnouncingKind() {
        PlayerMock player = server.addPlayer();

        assertThatCode(() -> hud(true).arrived(ref(player), TeleportKind.WARP)).doesNotThrowAnyException();

        assertThat(messages.keys).containsExactly(TeleportMessageKey.ARRIVED_TITLE);
    }

    @Test
    void skipsWhenTheToggleIsOff() {
        PlayerMock player = server.addPlayer();

        hud(false).arrived(ref(player), TeleportKind.WARP);

        assertThat(messages.keys).isEmpty();
    }

    @Test
    void skipsTheKindsAPlayerDidNotAskFor() {
        PlayerMock player = server.addPlayer();

        hud(true).arrived(ref(player), TeleportKind.RESPAWN);
        hud(true).arrived(ref(player), TeleportKind.ADMIN);

        assertThat(messages.keys).isEmpty();
    }

    @Test
    void noOpForAnOfflinePlayer() {
        PlayerRef ghost = new PlayerRef(UUID.randomUUID(), "Ghost");

        assertThatCode(() -> hud(true).arrived(ghost, TeleportKind.HOME)).doesNotThrowAnyException();

        assertThat(messages.keys).isEmpty();
    }

    private static final class RecordingMessages implements Messages {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key);
            return key.key();
        }
    }
}
