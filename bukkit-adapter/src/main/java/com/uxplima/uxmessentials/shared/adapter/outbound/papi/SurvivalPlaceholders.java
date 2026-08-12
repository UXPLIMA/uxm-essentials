package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code survival_*} placeholders: the personal switch each survival
 * mechanic carries, and whether the server runs that mechanic at all. Wired during survival wiring; with the
 * module disabled the seam is absent and every key degrades to the dash, which is the honest answer for a
 * mechanic that is not running.
 *
 * <p>The two questions are kept apart on purpose. A HUD that shows a player their own auto-pickup switch wants
 * {@link #active}, which is the PDC stamp {@code /autopickup} flips; a HUD that explains why nothing is being
 * picked up wants {@link #enabled}, which is the operator's config. Reading them together is how an operator
 * writes "auto-pickup: off (disabled server-side)" without guessing.
 */
public interface SurvivalPlaceholders {

    /** Whether the mechanic is switched on for {@code who}, taking the configured default when never toggled. */
    boolean active(PlayerRef who, Mechanic mechanic);

    /** Whether the server runs the mechanic at all, which is the operator's config rather than the player's. */
    boolean enabled(Mechanic mechanic);

    /** The survival mechanics that carry a personal switch. */
    enum Mechanic {
        TREE_FELLER,
        VEINMINER,
        FARM_PROTECT,
        AUTO_PICKUP,
        AUTO_SMELT,
        AUTO_SELL,
        AUTO_TOOL
    }
}
