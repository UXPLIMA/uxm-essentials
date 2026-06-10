package com.uxplima.uxmessentials.shared.application.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * The resilience invariant of a {@link HealthCheck}: {@link HealthCheck#safe()} converts any exception the probe
 * leaks into a {@code FAIL} result carrying its message, so one misbehaving check never aborts a {@code doctor}
 * run. Also pins {@link HealthStatus#worst} as the severity fold the aggregation relies on.
 */
class HealthCheckTest {

    @Test
    void safeConvertsAThrownProbeIntoAFailResultInsteadOfPropagating() {
        HealthCheck throwing = new HealthCheck() {
            @Override
            public String name() {
                return "db";
            }

            @Override
            public HealthResult check() {
                throw new IllegalStateException("connection refused");
            }
        };

        HealthCheck safe = throwing.safe();

        HealthResult[] captured = new HealthResult[1];
        assertThatCode(() -> captured[0] = safe.check()).doesNotThrowAnyException();
        assertThat(captured[0].status()).isEqualTo(HealthStatus.FAIL);
        assertThat(captured[0].message()).contains("connection refused");
        assertThat(safe.name()).isEqualTo("db");
    }

    @Test
    void safeIsTransparentForAWellBehavedCheck() {
        HealthCheck healthy = new HealthCheck() {
            @Override
            public String name() {
                return "ok";
            }

            @Override
            public HealthResult check() {
                return HealthResult.ok("all good");
            }
        };

        assertThat(healthy.safe().check()).isEqualTo(HealthResult.ok("all good"));
    }

    @Test
    void safeReportsTheExceptionTypeWhenItCarriesNoMessage() {
        HealthCheck nullMessage = new HealthCheck() {
            @Override
            public String name() {
                return "x";
            }

            @Override
            public HealthResult check() {
                throw new IllegalStateException();
            }
        };

        HealthResult result = nullMessage.safe().check();

        assertThat(result.status()).isEqualTo(HealthStatus.FAIL);
        assertThat(result.message()).contains("IllegalStateException");
    }

    @Test
    void worstPicksTheMoreSevereStatus() {
        assertThat(HealthStatus.OK.worst(HealthStatus.WARN)).isEqualTo(HealthStatus.WARN);
        assertThat(HealthStatus.WARN.worst(HealthStatus.FAIL)).isEqualTo(HealthStatus.FAIL);
        assertThat(HealthStatus.FAIL.worst(HealthStatus.OK)).isEqualTo(HealthStatus.FAIL);
        assertThat(HealthStatus.OK.worst(HealthStatus.OK)).isEqualTo(HealthStatus.OK);
    }
}
