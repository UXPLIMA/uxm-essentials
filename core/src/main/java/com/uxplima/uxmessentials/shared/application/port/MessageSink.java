package com.uxplima.uxmessentials.shared.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port that delivers a finalized rendered string to one viewer.
 *
 * <p>Split from {@link Messages} on purpose. Resolution is a pure synchronous function every
 * audit-emitter and result-renderer consumes, while delivery is a viewer-bound, region-hopping
 * primitive only the send paths consume. The adapter parses the MiniMessage source into a
 * {@code Component} exactly once here and bridges to the viewer's region thread via the injected
 * {@link Scheduler}. An offline or unknown viewer is a silent no-op.
 */
public interface MessageSink {

    /** Deliver an already-resolved MiniMessage source string to {@code viewer}. */
    void deliver(PlayerRef viewer, String renderedText);

    /**
     * {@link #deliver(PlayerRef, String)}, running {@code unreached} instead when the line cannot reach the viewer: they
     * are not in the world here (still logging in, or gone) by the time it would be shown. For a line that must not be
     * lost, so the caller can keep it. {@code unreached} may run on any thread. The default cannot tell and never runs
     * it.
     */
    default void deliver(PlayerRef viewer, String renderedText, Runnable unreached) {
        deliver(viewer, renderedText);
    }
}
