package com.uxplima.uxmessentials.shared.application.port;

import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.domain.claim.ClaimRegion;
import org.jspecify.annotations.NullMarked;

/**
 * Port through which a context learns that a land claim was removed, receiving the {@link ClaimRegion} the
 * claim occupied so it can cascade — for instance, suspend the player-warps that sat inside now-unclaimed
 * land. The adapter layer bridges the active claim plugin's own deletion events onto this port.
 *
 * <p><strong>Best-effort, not authoritative.</strong> Not every claim plugin publishes a deletion event, and
 * those that do may not cover every path that unclaims land (a bulk land-wipe that skips the per-chunk event,
 * for example). A consumer must therefore never rely on this signal for correctness — a missed event simply
 * leaves the downstream state untouched. The intended consumer defaults to "suspend, don't delete", so the
 * worst a dropped event causes is a warp left active in land that is no longer claimed, which is safe.
 *
 * <p><strong>Threading.</strong> A registered sink is invoked on the server (event) thread, inline with the
 * claim plugin's own event dispatch. A sink must therefore return promptly and do any heavy or blocking work
 * off-thread through the usual scheduler port rather than inside the callback.
 */
@NullMarked
public interface ClaimDeletionEvents {

    /**
     * Registers {@code sink} to be called once per removed claim with the region it occupied. Sinks are
     * additive: registering more than one fans every deletion out to all of them, in registration order.
     *
     * @param sink the callback invoked on the server thread when a claim is removed; never {@code null}
     */
    void onDeleted(Consumer<ClaimRegion> sink);
}
