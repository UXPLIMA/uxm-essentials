package com.uxplima.uxmessentials.shared.application.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The aggregation seam behind {@code /uxmess doctor}: a {@link HealthReport} folds a mix of {@code OK}/{@code
 * WARN}/{@code FAIL} check results into one overall status (the worst severity) and runs every check through the
 * {@link HealthCheck#safe()} guard so a probe that throws becomes a {@code FAIL} line, never an aborted run.
 */
class HealthReportTest {

    @Test
    void overallIsTheWorstStatusAcrossEntries() {
        HealthReport report = HealthReport.run(List.of(
                fixed("a", HealthResult.ok("fine")),
                fixed("b", HealthResult.warn("degraded")),
                fixed("c", HealthResult.fail("broken"))));

        assertThat(report.overall()).isEqualTo(HealthStatus.FAIL);
        assertThat(report.hasFailure()).isTrue();
        assertThat(report.entries()).hasSize(3);
    }

    @Test
    void warnIsTheWorstWhenNoCheckFails() {
        HealthReport report = HealthReport.run(
                List.of(fixed("a", HealthResult.ok("fine")), fixed("b", HealthResult.warn("degraded"))));

        assertThat(report.overall()).isEqualTo(HealthStatus.WARN);
        assertThat(report.hasFailure()).isFalse();
    }

    @Test
    void anEmptyReportIsHealthy() {
        HealthReport report = HealthReport.run(List.of());

        assertThat(report.overall()).isEqualTo(HealthStatus.OK);
        assertThat(report.hasFailure()).isFalse();
        assertThat(report.entries()).isEmpty();
    }

    @Test
    void entriesAreReportedInRunOrderWithTheirNames() {
        HealthReport report = HealthReport.run(
                List.of(fixed("database", HealthResult.ok("up")), fixed("economy", HealthResult.warn("x"))));

        assertThat(report.entries()).extracting(HealthReport.Entry::name).containsExactly("database", "economy");
    }

    @Test
    void aThrowingCheckBecomesAFailEntryRatherThanAbortingTheRun() {
        HealthCheck explodes = new HealthCheck() {
            @Override
            public String name() {
                return "boom";
            }

            @Override
            public HealthResult check() {
                throw new IllegalStateException("probe blew up");
            }
        };

        HealthReport report = HealthReport.run(List.of(fixed("ok", HealthResult.ok("up")), explodes));

        assertThat(report.entries()).hasSize(2);
        HealthReport.Entry boom = report.entries().get(1);
        assertThat(boom.name()).isEqualTo("boom");
        assertThat(boom.result().status()).isEqualTo(HealthStatus.FAIL);
        assertThat(boom.result().message()).contains("probe blew up");
        assertThat(report.overall()).isEqualTo(HealthStatus.FAIL);
    }

    private static HealthCheck fixed(String name, HealthResult result) {
        return new HealthCheck() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public HealthResult check() {
                return result;
            }
        };
    }
}
