package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.claim.ClaimPolicy;
import com.uxplima.uxmessentials.shared.application.claim.ClaimPolicySettings;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import org.jspecify.annotations.NullMarked;

/**
 * Adapter binding the {@link ClaimService} port to a {@link ClaimPolicy} driven by the discovered
 * {@link ClaimProvider}. The homes context asks this service before setting or teleporting to a home; the
 * service translates the kernel's {@link PlayerRef}/{@link Position} into the policy's block-coordinate calls.
 *
 * <p>When the provider is inactive (no claim plugin), the policy short-circuits every check to
 * {@link ClaimDecision#ALLOWED}, so wiring this service unconditionally is safe regardless of what is installed.
 */
@NullMarked
public final class ClaimServiceImpl implements ClaimService {

    private final ClaimPolicy policy;

    public ClaimServiceImpl(ClaimProvider provider, ClaimPolicySettings settings) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(settings, "settings");
        this.policy = new ClaimPolicy(provider, settings);
    }

    @Override
    public ClaimDecision canPlace(PlayerRef who, Position at) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(at, "at");
        return policy.canPlace(who.uuid(), at.world(), at.blockX(), at.blockZ());
    }

    @Override
    public ClaimDecision canAccess(PlayerRef who, Position at) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(at, "at");
        return policy.canAccess(who.uuid(), at.world(), at.blockX(), at.blockZ());
    }

    @Override
    public boolean isProtected(Position at) {
        Objects.requireNonNull(at, "at");
        return policy.isWithinClaim(at.world(), at.blockX(), at.blockZ());
    }
}
