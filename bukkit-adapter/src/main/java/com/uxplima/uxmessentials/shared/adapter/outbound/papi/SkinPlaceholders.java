package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code skin_*} placeholders: which skin a player chose, where it came
 * from, and which player model it was cut for. A player who chose nothing reads the dash, which is honest: they
 * wear whatever the join order gave them rather than a choice of their own.
 *
 * <p>Wired during skin wiring, over the same cached repository {@code /skin info} reads, so a HUD refresh costs a
 * memory read rather than a query. With the module disabled the seam is absent and every key degrades to the dash.
 */
public interface SkinPlaceholders {

    /** Where {@code who}'s chosen skin came from, lowercased ({@code by-name}, {@code by-url}, ...). */
    Optional<String> source(PlayerRef who);

    /** What that source names: the account, the link, the file or the xuid. */
    Optional<String> value(PlayerRef who);

    /** The player model it was cut for, lowercased ({@code classic} or {@code slim}). */
    Optional<String> model(PlayerRef who);
}
