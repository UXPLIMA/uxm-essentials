package com.uxplima.uxmessentials.security.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;

/**
 * The operator's recovery path behind {@code /security reset <player> [totp|pin|all]}: clear a target's factors
 * without proving them. Every self-service removal demands the factor it is removing, which is what makes them safe,
 * and which is also why a player who loses their authenticator or forgets their PIN would otherwise be locked out of
 * their own account forever. This is the one door that opens without a proof, so it is operator-gated and its every
 * use is logged by the caller.
 *
 * <p>The scope is explicit ({@link FactorScope}) rather than "remove whatever they have": clearing a lost
 * authenticator must not take a PIN the player still knows. When the reset leaves the target holding no factor at
 * all, their device trusts are revoked too, so nothing is left pointing at a registration that no longer exists.
 *
 * <p>This holds no Bukkit type and never touches the tick thread; the command runs it off-thread.
 */
public final class ResetFactors {

    private final TwoFactorRepository repository;
    private final TrustStore trustStore;

    public ResetFactors(TwoFactorRepository repository, TrustStore trustStore) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.trustStore = Objects.requireNonNull(trustStore, "trustStore");
    }

    /** Clear {@code scope} for {@code playerId}, or report that they held nothing in that scope to clear. */
    public ResetResult reset(UUID playerId, FactorScope scope) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(scope, "scope");
        TwoFactorRegistration registration = repository.find(playerId).orElse(null);
        if (registration == null || !holdsSomethingIn(registration, scope)) {
            return ResetResult.NOTHING_TO_RESET;
        }
        switch (scope) {
            case TOTP -> repository.clearTotp(playerId);
            case PIN -> repository.clearPin(playerId);
            case ALL -> repository.delete(playerId);
        }
        // A target left with no factor has nothing to verify against, so any remembered device is meaningless.
        if (repository.find(playerId).map(TwoFactorRegistration::hasAnyFactor).orElse(false)) {
            return ResetResult.RESET;
        }
        trustStore.revoke(playerId);
        return ResetResult.RESET;
    }

    /** Whether the registration holds at least one factor the requested scope would actually clear. */
    private static boolean holdsSomethingIn(TwoFactorRegistration registration, FactorScope scope) {
        return switch (scope) {
            case TOTP -> registration.totpEnabled();
            case PIN -> registration.pinSet();
            case ALL -> registration.hasAnyFactor();
        };
    }

    /** The outcome of a reset: the requested factors were cleared, or the target held none of them. */
    public enum ResetResult {
        RESET,
        NOTHING_TO_RESET
    }
}
