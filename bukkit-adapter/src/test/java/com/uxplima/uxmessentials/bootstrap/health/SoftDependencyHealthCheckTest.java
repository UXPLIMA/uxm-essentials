package com.uxplima.uxmessentials.bootstrap.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import com.uxplima.uxmessentials.shared.application.health.HealthStatus;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * What {@code /uxmess doctor} tells an operator about optional integrations. The point of the check is that it
 * covers everything we integrate with rather than a hand-kept shortlist, so the assertions are about coverage
 * and about naming what actually bound, not about the exact sentence.
 */
class SoftDependencyHealthCheckTest {

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
    void withNoIntegrationsInstalledItReportsNonePresentAndStaysOk() {
        HealthResult result = check();

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
        assertThat(result.message()).contains("0/").contains("integrations");
    }

    @Test
    void anInstalledIntegrationIsNamedWithItsFamily() {
        MockBukkit.createMockPlugin("Lands");

        HealthResult result = check();

        assertThat(result.message()).contains("1/").contains("land claims: Lands");
    }

    @Test
    void severalIntegrationsAreGroupedByFamily() {
        MockBukkit.createMockPlugin("Lands");
        MockBukkit.createMockPlugin("PlaceholderAPI");

        HealthResult result = check();

        assertThat(result.message())
                .contains("2/")
                .contains("placeholders: PlaceholderAPI")
                .contains("land claims: Lands");
    }

    private HealthResult check() {
        return new SoftDependencyHealthCheck(server.getPluginManager(), new DefaultsOnlyConfig()).check();
    }

    /** A config that answers every read with the caller's default, which leaves the network bus disabled. */
    private static final class DefaultsOnlyConfig implements ConfigStore {

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}
