package com.uxplima.uxmessentials.shared.adapter.outbound.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.ClientProtocol;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The protocol source's binding rule and its degrade. ViaVersion's API is not on the test classpath, so the
 * live read cannot be exercised here; what can be, and what actually decides behaviour on a real server, is
 * that a server without ViaVersion never constructs the reader at all, and that a server with ViaVersion
 * installed but unreachable answers {@code UNKNOWN} instead of throwing into a menu render.
 */
class ViaVersionClientProtocolTest {

    private static final PlayerRef SOMEONE = new PlayerRef(UUID.randomUUID(), "Someone");

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void withoutViaVersionTheSourceIsTheUnavailableOne() {
        assertThat(ViaVersionClientProtocol.bind(server, SILENT)).isSameAs(ClientProtocol.UNAVAILABLE);
    }

    @Test
    void theUnavailableSourceAnswersUnknownForEveryone() {
        assertThat(ClientProtocol.UNAVAILABLE.protocolVersion(SOMEONE)).isEqualTo(ClientProtocol.UNKNOWN);
    }

    @Test
    void withViaVersionInstalledTheReaderIsBound() {
        MockBukkit.createMockPlugin("ViaVersion");

        assertThat(ViaVersionClientProtocol.bind(server, SILENT)).isNotSameAs(ClientProtocol.UNAVAILABLE);
    }

    @Test
    void anUnreachableViaApiAnswersUnknownRatherThanThrowing() {
        MockBukkit.createMockPlugin("ViaVersion");
        ClientProtocol protocol = ViaVersionClientProtocol.bind(server, SILENT);

        assertThatCode(() -> assertThat(protocol.protocolVersion(SOMEONE)).isEqualTo(ClientProtocol.UNKNOWN))
                .doesNotThrowAnyException();
    }

    private static final Logger SILENT = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };
}
