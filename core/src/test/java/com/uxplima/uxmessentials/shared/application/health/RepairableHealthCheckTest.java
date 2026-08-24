package com.uxplima.uxmessentials.shared.application.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RepairableHealthCheckTest {

    @Test
    void aThrowingRepairBecomesAFailedResult() {
        RepairableHealthCheck check = new RepairableHealthCheck() {
            @Override
            public String name() {
                return "data";
            }

            @Override
            public HealthResult check() {
                return HealthResult.warn("broken");
            }

            @Override
            public RepairResult repair() {
                throw new IllegalStateException("database unavailable");
            }
        };

        RepairResult result = check.repairSafely();

        assertThat(result.status()).isEqualTo(RepairStatus.FAILED);
        assertThat(result.message()).contains("database unavailable");
    }
}
