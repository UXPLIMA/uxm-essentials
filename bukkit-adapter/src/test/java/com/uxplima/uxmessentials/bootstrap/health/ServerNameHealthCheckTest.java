package com.uxplima.uxmessentials.bootstrap.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.DuplicateServerNames;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import com.uxplima.uxmessentials.shared.application.health.HealthStatus;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import org.junit.jupiter.api.Test;

/** The doctor fails on a name another backend uses and warns on the shipped default. */
class ServerNameHealthCheckTest {

    private static final long NOW = 1_700_000_000_000L;

    private static final Logger QUIET = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void error(String message, Object... args) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    private static DuplicateServerNames watching(String name) {
        return new DuplicateServerNames(name, 7L, Duration.ofSeconds(90), QUIET);
    }

    private static HealthResult check(DuplicateServerNames duplicates) {
        return new ServerNameHealthCheck(duplicates, () -> NOW).check();
    }

    @Test
    void aNameAnotherBackendAnnouncesFails() {
        DuplicateServerNames duplicates = watching("survival-1");
        duplicates.inspect(new ServerPing("survival-1", NOW, 99L));

        HealthResult result = check(duplicates);

        assertThat(result.status()).isEqualTo(HealthStatus.FAIL);
        assertThat(result.message()).contains("survival-1", "network.server-name");
    }

    @Test
    void theShippedDefaultWarns() {
        HealthResult result = check(watching("server-1"));

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message()).contains("network.server-name");
    }

    @Test
    void aNameOfItsOwnPasses() {
        assertThat(check(watching("survival-1")).status()).isEqualTo(HealthStatus.OK);
    }
}
