package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

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
        Map<String, Object> values = new HashMap<>();
        values.put("arrival-title", enabled);
        TeleportSettings settings = new TeleportSettings(new FixedConfig(values));
        return new TeleportArrivalHud(messages, server, settings, new InlineScheduler());
    }

    private TeleportArrivalHud hud(Map<String, Object> customValues) {
        TeleportSettings settings = new TeleportSettings(new FixedConfig(customValues));
        return new TeleportArrivalHud(messages, server, settings, new InlineScheduler());
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    @Test
    void showsTheArrivalTitleForAnAnnouncingKind() {
        PlayerMock player = server.addPlayer();

        assertThatCode(() -> hud(true).arrived(ref(player), TeleportKind.HOME)).doesNotThrowAnyException();

        assertThat(messages.keys).containsExactly(TeleportMessageKey.ARRIVED_TITLE);
    }

    @Test
    void skipsWhenTheToggleIsOff() {
        PlayerMock player = server.addPlayer();

        hud(false).arrived(ref(player), TeleportKind.HOME);

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

    @Test
    void supportsCustomMessageTextAndType() {
        PlayerMock player = server.addPlayer();
        Map<String, Object> values = new HashMap<>();
        values.put("arrival-title", true);
        values.put("arrival-messages.home.message", "Welcome Home!");
        values.put("arrival-messages.home.type", "CHAT");

        hud(values).arrived(ref(player), TeleportKind.HOME);

        // Since it's a raw string, it bypasses messages.resolve keys but sends message
        assertThat(player.nextMessage()).isEqualTo("Welcome Home!");
        assertThat(messages.keys).isEmpty();
    }

    @Test
    void supportsMultipleConcurrentMessagesAndCustomTimings() {
        PlayerMock player = server.addPlayer();
        Map<String, Object> values = new HashMap<>();
        values.put("arrival-title", true);
        values.put(
                "arrival-messages.home",
                List.of(
                        "CHAT;First chat message",
                        "CHAT;Second chat message",
                        "TITLE;Custom Title;500;2000;500",
                        "SUBTITLE;Custom Subtitle;500;2000;500"));

        hud(values).arrived(ref(player), TeleportKind.HOME);

        assertThat(player.nextMessage()).isEqualTo("First chat message");
        assertThat(player.nextMessage()).isEqualTo("Second chat message");
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

    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }

        @SuppressWarnings("unchecked")
        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            if (values.get(path) instanceof List<?> list) {
                return (List<String>) list;
            }
            return List.copyOf(fallback);
        }
    }

    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(com.uxplima.uxmessentials.shared.domain.Position position, Runnable task) {
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
        public void asyncAfter(java.time.Duration delay, Runnable task) {
            task.run();
        }
    }
}
