package com.uxplima.uxmessentials.security.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.application.port.VerifiedSessions;

/**
 * The read-write behind {@code /2fa force <player>}: put an enrolled target back into the state where the
 * join-verification freeze will make them prove their second factor again. It reads the target's registration and,
 * when they hold a factor, forgets every device trust they have through the {@link TrustStore}, and any proof they could carry to another
 * backend, the durable forced
 * state, so their next join is no longer skipped by the trusted-device bypass and survives a restart. A target with no
 * factor cannot be made to verify against nothing, so nothing is revoked and the caller is told they are not enrolled.
 *
 * <p>This holds no Bukkit type and never touches the tick thread; the command runs it off-thread. The immediate freeze
 * of a target who is online right now is a separate, Bukkit-side concern the verification controller owns - this use
 * case only sets the persistent state and reports whether there was anything to force.
 */
public final class ForceReverification {

    private final TwoFactorRepository repository;
    private final TrustStore trustStore;
    private final VerifiedSessions sessions;

    public ForceReverification(TwoFactorRepository repository, TrustStore trustStore, VerifiedSessions sessions) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.trustStore = Objects.requireNonNull(trustStore, "trustStore");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /**
     * Force {@code playerId} to re-verify: revoke their device trusts when they are enrolled, or report that there is
     * no factor to force when they are not.
     */
    public ForceResult force(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        TwoFactorRegistration registration = repository.find(playerId).orElse(null);
        if (registration == null || !registration.hasAnyFactor()) {
            return ForceResult.NOT_ENROLLED;
        }
        trustStore.revoke(playerId);
        // A proof carried from another backend would let them past the freeze this is meant to put them through.
        sessions.revoke(playerId);
        return ForceResult.FORCED;
    }

    /** The outcome of a force attempt: the target was enrolled and is now forced, or held no factor to force. */
    public enum ForceResult {
        FORCED,
        NOT_ENROLLED
    }
}
