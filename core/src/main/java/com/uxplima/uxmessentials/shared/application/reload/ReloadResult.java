package com.uxplima.uxmessentials.shared.application.reload;

import java.util.Objects;

/**
 * The outcome of one reload step: its {@link ReloadStatus} and a short operator-facing detail line describing what
 * actually happened ("config re-read from disk", "no live-reload hook").
 *
 * @param status how the step ended
 * @param message the one-line detail rendered next to the step's name
 */
public record ReloadResult(ReloadStatus status, String message) {

    public ReloadResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
    }

    /** The step re-read its source and the new values are live. */
    public static ReloadResult applied(String message) {
        return new ReloadResult(ReloadStatus.APPLIED, message);
    }

    /** The step's subsystem keeps its current state until the server restarts. */
    public static ReloadResult restartRequired(String message) {
        return new ReloadResult(ReloadStatus.RESTART_REQUIRED, message);
    }

    /** The step failed, so the previous state is still in force. */
    public static ReloadResult failed(String message) {
        return new ReloadResult(ReloadStatus.FAILED, message);
    }
}
