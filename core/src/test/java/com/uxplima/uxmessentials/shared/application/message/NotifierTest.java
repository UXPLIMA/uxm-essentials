package com.uxplima.uxmessentials.shared.application.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class NotifierTest {

    private static final PlayerRef VIEWER = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final Messages MESSAGES =
            (viewer, key, placeholders) -> "resolved:" + key.key() + ":" + placeholders.getOrDefault("world", "");

    @Test
    void resolvesThenDelivers() {
        StringBuilder delivered = new StringBuilder();
        MessageSink sink = (viewer, text) -> delivered.append(text);

        new Notifier(MESSAGES, sink).send(VIEWER, SharedMessageKey.COMMAND_PLAYERS_ONLY, Map.of("world", "creative"));

        assertThat(delivered.toString())
                .isEqualTo("resolved:" + SharedMessageKey.COMMAND_PLAYERS_ONLY.key() + ":creative");
    }

    @Test
    void sendsWithoutPlaceholders() {
        StringBuilder delivered = new StringBuilder();
        MessageSink sink = (viewer, text) -> delivered.append(text);

        new Notifier(MESSAGES, sink).send(VIEWER, SharedMessageKey.COMMAND_PLAYERS_ONLY);

        assertThat(delivered.toString()).isEqualTo("resolved:" + SharedMessageKey.COMMAND_PLAYERS_ONLY.key() + ":");
    }

    @Test
    void rendersWithoutDelivering() {
        StringBuilder delivered = new StringBuilder();
        MessageSink sink = (viewer, text) -> delivered.append(text);

        String rendered = new Notifier(MESSAGES, sink)
                .render(VIEWER, SharedMessageKey.COMMAND_PLAYERS_ONLY, Map.of("world", "nether"));

        assertThat(rendered).isEqualTo("resolved:" + SharedMessageKey.COMMAND_PLAYERS_ONLY.key() + ":nether");
        assertThat(delivered.toString()).isEmpty();
    }
}
