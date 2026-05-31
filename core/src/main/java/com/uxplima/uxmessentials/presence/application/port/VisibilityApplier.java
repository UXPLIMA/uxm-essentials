package com.uxplima.uxmessentials.presence.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port that applies a player's vanish state to the live server view: hiding a newly-vanished player
 * from everyone who lacks the vanish-see node (and revealing them again on unvanish), and reconciling
 * visibility when a player joins (a relog of a vanished staff member, or a fresh join arriving while others are
 * vanished). The adapter drives Bukkit's {@code Player#hidePlayer} / {@code showPlayer} graph — the same graph
 * the messaging and teleport contexts read through {@code canSee}, which is what makes a vanished player
 * disappear from {@code /msg} resolution and {@code /tpa} listings without those contexts importing presence.
 *
 * <p>Every method hops to the owning region/entity thread through the {@code Scheduler} port inside the
 * adapter; the use cases call these from any thread and never touch a live {@code Player} themselves.
 */
public interface VisibilityApplier {

    /** Hide {@code who} from every viewer who cannot see vanished players. Called when {@code who} vanishes. */
    void hide(PlayerRef who);

    /** Reveal {@code who} to everyone again. Called when {@code who} unvanishes. */
    void reveal(PlayerRef who);

    /**
     * Reconcile visibility for a player who just joined: re-hide them if they are vanished (persisted vanish),
     * and hide from them anyone currently vanished they may not see. Called from the join listener so the
     * vanish graph is correct the moment a player connects.
     */
    void reconcileOnJoin(PlayerRef who, boolean vanished);
}
