package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code %uxmessentials_rank%}, {@code %uxmessentials_rank_next%} and
 * {@code %uxmessentials_prestige%} placeholders. It is an adapter over the ranks context's {@code CurrentRank}
 * read and the parsed ladder, wired during ranks wiring; when the ranks module is disabled the seam is absent
 * and each placeholder degrades to the dash.
 *
 * <p>The pointer is DB-backed, so a player's standing resolves for an offline requester too — there is no
 * offline guard on these keys. The single {@link #standing(PlayerRef)} read carries the current rank display
 * name, the next rank's display name (empty at the top of the ladder, which the resolver renders as its
 * max-rank marker), and the prestige level, so one lookup answers all three placeholders.
 */
public interface RanksPlaceholders {

    /**
     * {@code who}'s resolved standing, or empty only when the ladder holds no ranks (an operator cleared
     * {@code ranks.conf}), in which case every ranks placeholder degrades to the dash.
     */
    Optional<Standing> standing(PlayerRef who);

    /**
     * A player's placeholder-facing standing: the display name of the rank they hold, the display name of the
     * next rank up ({@link Optional#empty()} at the top rank), and their prestige level.
     *
     * @param rank the current rank's display name
     * @param next the next rank's display name, or empty when the player is already at the highest rank
     * @param prestige the player's prestige level
     */
    record Standing(String rank, Optional<String> next, int prestige) {

        public Standing {
            Objects.requireNonNull(rank, "rank");
            Objects.requireNonNull(next, "next");
        }
    }
}
