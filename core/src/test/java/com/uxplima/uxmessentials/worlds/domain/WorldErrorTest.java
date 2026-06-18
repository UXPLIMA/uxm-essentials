package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import org.junit.jupiter.api.Test;

class WorldErrorTest {

    @Test
    void everyErrorMapsToANonNullMessageKey() {
        for (WorldError error : WorldError.values()) {
            MessageKey key = error.messageKey();
            assertThat(key).isNotNull();
            assertThat(key.key()).startsWith("world.");
        }
    }
}
