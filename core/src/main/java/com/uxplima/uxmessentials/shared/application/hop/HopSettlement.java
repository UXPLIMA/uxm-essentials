package com.uxplima.uxmessentials.shared.application.hop;

import java.util.Objects;

/**
 * Money work the origin left for the landing: once the player stands at the destination, the hop's charge is paid
 * on to {@code payee}, a reference the kind's {@link HopResolver} reads (for a player warp, the warp whose owner
 * banks the share). It is paid only after a landing because until then the charge may still have to go back.
 *
 * @param payee the kind-specific reference the charge is settled to
 */
public record HopSettlement(String payee) {

    public HopSettlement {
        Objects.requireNonNull(payee, "payee");
        if (payee.isBlank()) {
            throw new IllegalArgumentException("a settlement needs a payee");
        }
    }
}
