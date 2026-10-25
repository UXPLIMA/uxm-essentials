package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam for the {@code bossbar_*} placeholders, over the bars on a player's screen. Wired during the boss bar
 * module's wiring; when the module is switched off the seam is absent and every key reads the dash.
 */
public interface BossBarPlaceholders {

    /** Whether {@code who} has not hidden their bars with {@code /bossbars}. */
    boolean visible(PlayerRef who);

    /** The ids of the bars on {@code who}'s screen, top first. */
    List<String> shown(PlayerRef who);

    /** The time left on {@code bar} for {@code who}, in their words, or empty when it has no clock running. */
    Optional<String> remaining(PlayerRef who, String bar);
}
