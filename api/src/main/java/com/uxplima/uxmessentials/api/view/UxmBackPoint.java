package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;

/**
 * Where {@code /back} would return a player to, and why that place was recorded.
 *
 * <p>The cause matters as much as the place: an operator can switch off returning to a death site, in which case a
 * point recorded at a death is held but refused.
 *
 * @param location where the player was
 * @param cause what put them somewhere else
 * @param capturedAt when it was recorded
 */
public record UxmBackPoint(UxmLocation location, UxmBackCause cause, Instant capturedAt) {

    public UxmBackPoint {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(capturedAt, "capturedAt");
    }
}
