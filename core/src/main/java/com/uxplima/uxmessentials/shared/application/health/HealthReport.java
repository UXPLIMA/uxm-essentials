package com.uxplima.uxmessentials.shared.application.health;

import java.util.List;
import java.util.Objects;

/**
 * The aggregate outcome of a {@code /uxmess doctor} run: the named result of every check that ran, plus the
 * overall status folded as the worst severity across them ({@code OK} only when every check is {@code OK}). The
 * command renders one line per entry and a closing summary when {@link #overall()} is {@link HealthStatus#FAIL}.
 *
 * @param entries the per-check outcomes in run order
 */
public record HealthReport(List<Entry> entries) {

    public HealthReport {
        entries = List.copyOf(entries);
    }

    /** Run every check (each through {@link HealthCheck#safe()}) and collect the results into a report. */
    public static HealthReport run(List<HealthCheck> checks) {
        Objects.requireNonNull(checks, "checks");
        List<Entry> results = checks.stream()
                .map(check -> {
                    HealthCheck safe = check.safe();
                    return new Entry(safe.name(), safe.check());
                })
                .toList();
        return new HealthReport(results);
    }

    /** The worst severity across every entry; {@link HealthStatus#OK} when the report is empty. */
    public HealthStatus overall() {
        return entries.stream().map(entry -> entry.result().status()).reduce(HealthStatus.OK, HealthStatus::worst);
    }

    /** Whether at least one check reported {@link HealthStatus#FAIL}. */
    public boolean hasFailure() {
        return overall() == HealthStatus.FAIL;
    }

    /**
     * One line of a report: the check name and its result.
     *
     * @param name the check's label
     * @param result the check's outcome
     */
    public record Entry(String name, HealthResult result) {
        public Entry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(result, "result");
        }
    }
}
