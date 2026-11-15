package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.WarpCost;

/**
 * Gives back what the origin charged for a cross-server request that did not move the player. Only a caller that
 * claimed the request (its store delete removed the row) may call this, which is what keeps the refund single.
 * The credit goes through the same {@link PlayerWarpEconomy} seam the charge used, and the player is told only
 * once the credit has actually landed.
 */
final class CrossServerRefund {

    private final Optional<PlayerWarpEconomy> economy;
    private final Notifier notifier;

    CrossServerRefund(Optional<PlayerWarpEconomy> economy, Notifier notifier) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Credit {@code pending}'s exact charge back to {@code who}; nothing happens when nothing was charged. */
    void give(PlayerRef who, PendingTeleport pending, String warpLabel) {
        if (economy.isEmpty() || pending.paid().isEmpty()) {
            return;
        }
        WarpCost cost = pending.paid().get();
        if (economy.get().refund(who, cost.amount(), cost.currencyId()).isOk()) {
            notifier.send(who, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_REFUNDED, Map.of("warp", warpLabel));
        }
    }
}
