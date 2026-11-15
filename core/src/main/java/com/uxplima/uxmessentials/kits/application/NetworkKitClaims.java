package com.uxplima.uxmessentials.kits.application;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.ledger.NetworkClaims;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The {@link KitClaimStore} a network uses: one-time claims kept in the shared database through {@link NetworkClaims},
 * so a kit claimed on one backend stays claimed on every other. The claim is taken in the database before the grant,
 * which is what stops two backends from both handing out the kit to a player who switched between them.
 *
 * <p>Unlike the PDC store, a reset reaches an offline player too: their rows are deleted, and they read the change on
 * their next login.
 */
public final class NetworkKitClaims implements KitClaimStore {

    private final NetworkClaims claims;

    public NetworkKitClaims(NetworkClaims claims) {
        this.claims = Objects.requireNonNull(claims, "claims");
    }

    @Override
    public boolean hasClaimed(PlayerRef who, KitId kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        return claims.hasKit(who.uuid(), kit.value());
    }

    @Override
    public void markClaimed(PlayerRef who, KitId kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        claims.markKit(who.uuid(), kit.value());
    }

    @Override
    public void reset(PlayerRef who, KitId kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        claims.releaseKit(who.uuid(), kit.value());
    }

    @Override
    public void resetAll(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        claims.releaseAllKits(who.uuid());
    }

    @Override
    public boolean ready(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return claims.ready(who.uuid());
    }

    @Override
    public CompletableFuture<Boolean> acquire(PlayerRef who, KitId kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        return claims.acquireKit(who, kit.value());
    }

    @Override
    public void release(PlayerRef who, KitId kit) {
        reset(who, kit);
    }
}
