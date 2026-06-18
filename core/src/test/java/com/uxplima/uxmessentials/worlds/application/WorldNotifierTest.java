package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class WorldNotifierTest {

    @Test
    void resolvesThenDelivers() {
        PlayerRef viewer = new PlayerRef(UUID.randomUUID(), "Steve");
        StringBuilder delivered = new StringBuilder();
        Messages messages = (v, key, placeholders) -> "resolved:" + key.key() + ":" + placeholders.get("world");
        MessageSink sink = (v, text) -> delivered.append(text);

        new WorldNotifier(messages, sink).send(viewer, WorldsMessageKey.WORLD_CREATED, Map.of("world", "creative"));

        assertThat(delivered.toString()).isEqualTo("resolved:world.created:creative");
    }
}
