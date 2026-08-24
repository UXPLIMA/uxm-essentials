package com.uxplima.uxmessentials.shared.application.health;

/**
 * A health check with a conservative, explicitly confirmed repair operation.
 *
 * <p>The normal {@link #check()} path is always read-only. {@link #repair()} may mutate only inconsistencies whose
 * intended state is unambiguous, and must be idempotent so retrying it is safe for an operator or automation.
 */
public interface RepairableHealthCheck extends HealthCheck {

    /** Repair safe inconsistencies and report exactly what changed. */
    RepairResult repair();

    /** Run {@link #repair()} behind the same fault boundary used for health probes. */
    default RepairResult repairSafely() {
        try {
            return repair();
        } catch (RuntimeException repairFailure) {
            String detail = repairFailure.getMessage();
            return RepairResult.failed("repair errored: " + (detail == null ? repairFailure : detail));
        }
    }
}
