package com.uxplima.uxmessentials.shared.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import org.jspecify.annotations.NullMarked;

/**
 * Application-layer port for claim-based placement and access checks. The homes context asks this
 * service before setting or teleporting to a home; the adapter wires it to a {@link ClaimPolicy}
 * backed by whatever claim plugin is active, or to {@link
 * com.uxplima.uxmessentials.shared.application.claim.AlwaysAllowClaimService} when none is.
 */
@NullMarked
public interface ClaimService {

    /**
     * Returns whether {@code who} may place a home at {@code at}. The decision reflects the
     * operator-configured claim policy (require-claim, block-foreign-claims, proximity distance).
     */
    ClaimDecision canPlace(PlayerRef who, Position at);

    /**
     * Returns whether {@code who} may teleport to {@code at}. The decision reflects whether the
     * player still has access to the claim covering that position.
     */
    ClaimDecision canAccess(PlayerRef who, Position at);
}
