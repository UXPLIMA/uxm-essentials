package com.uxplima.uxmessentials.worlds.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/**
 * Drives the asynchronous chunk pre-generation of a loaded world. The engine behind this port owns the
 * tick-paced generation loop, the in-flight concurrency cap, and the completion notification; the use
 * case in front of it only validates the request and reports the immediate command outcome.
 *
 * <p>At most one pre-generation runs per world at a time. {@link #start} refuses with {@link
 * WorldError#PREGEN_ALREADY_RUNNING} when one is already active for that world; {@link #isRunning}
 * reflects whether one is. {@link #stopAll} cancels every running pre-generation and is the seam the
 * module's stop path calls so no generation loop outlives a disable.
 */
public interface WorldPregen {

    /**
     * Begins generating the {@code (2*radius+1)} square of chunks around the world's spawn, returning
     * {@link WorldError#PREGEN_ALREADY_RUNNING} when a pre-generation is already active for {@code world}.
     */
    Result<Unit, WorldError> start(PlayerRef initiator, WorldName world, int radius);

    /** Cancels the active pre-generation of {@code world}, returning whether one was running to cancel. */
    boolean cancel(WorldName world);

    /** Whether a pre-generation is currently active for {@code world}. */
    boolean isRunning(WorldName world);

    /** Cancels every running pre-generation; called when the worlds module stops. */
    void stopAll();
}
