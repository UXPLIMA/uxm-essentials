package com.uxplima.uxmessentials.shared.application.port;

import com.uxplima.uxmessentials.shared.domain.DomainProposal;

/**
 * Asks whatever is outside the plugin whether an action may proceed.
 *
 * <p>A use case calls this after its own cheap checks and before it writes anything: by then the operation is known
 * to be valid, so a refusal is somebody else's policy rather than a duplicate of a rule we already enforce, and
 * nothing has to be undone when the answer is no.
 *
 * <p>The contract is deliberately narrow. It answers a boolean, it never throws, and it defaults to allowing:
 * a gate that cannot reach whoever would answer must let the action through, because a plugin failing open is an
 * unpoliced action while a plugin failing closed is a server where nobody can set a home. The adapter implementing
 * it is also responsible for costing nothing when nobody is asking to be consulted.
 */
public interface DomainGate {

    /** Whether {@code proposal} may proceed. Always {@code true} when nothing is listening. */
    boolean allows(DomainProposal proposal);

    /** The gate for a build with no adapter behind it, and the one the pure use-case tests default to. */
    static DomainGate allowAll() {
        return proposal -> true;
    }
}
