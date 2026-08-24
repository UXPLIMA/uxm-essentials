package com.uxplima.uxmessentials.shared.application.health;

import java.util.Objects;

/** One repair task's status and concise operator-facing result. */
public record RepairResult(RepairStatus status, String message) {

    public RepairResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
    }

    public static RepairResult unchanged(String message) {
        return new RepairResult(RepairStatus.UNCHANGED, message);
    }

    public static RepairResult repaired(String message) {
        return new RepairResult(RepairStatus.REPAIRED, message);
    }

    public static RepairResult failed(String message) {
        return new RepairResult(RepairStatus.FAILED, message);
    }
}
