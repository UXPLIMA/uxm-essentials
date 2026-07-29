package com.uxplima.uxmessentials.bootstrap.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import com.uxplima.uxmessentials.shared.application.health.HealthStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * What {@code /uxmess doctor} tells an operator about plugins that own the same command names we do. The value of
 * the check is that it names them, so the assertions are about which plugins are reported and that the report is
 * a warning rather than a failure, not about the exact sentence.
 */
class CommandConflictHealthCheckTest {

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
    void withNoConflictingPluginInstalledItIsOk() {
        HealthResult result = check();

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
        assertThat(result.message()).contains("no plugin with overlapping commands");
    }

    @Test
    void anInstalledCompetitorIsNamedWithTheSurfaceItOverlaps() {
        MockBukkit.createMockPlugin("Essentials");

        HealthResult result = check();

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message()).contains("Essentials").contains("homes, warps, economy");
    }

    @Test
    void severalInstalledCompetitorsAreAllNamed() {
        MockBukkit.createMockPlugin("Essentials");
        MockBukkit.createMockPlugin("CMI");

        HealthResult result = check();

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message()).contains("Essentials").contains("CMI");
    }

    @Test
    void aWorldManagerConflictsOnlyOnWorldCommands() {
        MockBukkit.createMockPlugin("Multiverse-Core");

        HealthResult result = check();

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message()).contains("Multiverse-Core").contains("world management");
        assertThat(result.message()).as("nothing else is installed").doesNotContain("Essentials");
    }

    @Test
    void anUnrelatedPluginIsNotAConflict() {
        // Only the named competitors count; a plugin we integrate with owns none of our command names.
        MockBukkit.createMockPlugin("LuckPerms");

        assertThat(check().status()).isEqualTo(HealthStatus.OK);
    }

    private HealthResult check() {
        return new CommandConflictHealthCheck(server.getPluginManager()).check();
    }
}
