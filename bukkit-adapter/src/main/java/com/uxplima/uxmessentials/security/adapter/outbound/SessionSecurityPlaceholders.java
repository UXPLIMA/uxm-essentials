package com.uxplima.uxmessentials.security.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.security.adapter.VerificationSessions;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.SecurityPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link SecurityPlaceholders} seam over the in-memory challenge registry and the module's own join-verification
 * switch. Both reads are a map lookup and a boolean field: nothing here touches the two-factor store, so a HUD that
 * shows "verifying" never turns a refresh into a database query.
 */
@NullMarked
public final class SessionSecurityPlaceholders implements SecurityPlaceholders {

    private final VerificationSessions sessions;
    private final boolean joinVerification;

    public SessionSecurityPlaceholders(VerificationSessions sessions, boolean joinVerification) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.joinVerification = joinVerification;
    }

    @Override
    public boolean verifying(PlayerRef who) {
        return sessions.isPending(Objects.requireNonNull(who, "who").uuid());
    }

    @Override
    public boolean enforced() {
        return joinVerification;
    }
}
