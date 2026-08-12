package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Optional;

/**
 * Read seam the expansion queries for the {@code servertweaks_*} placeholders. The module's tweaks are silent
 * server-wide side effects rather than per-player state, so the one thing it can answer is the brand it reports to
 * clients: the string a player reads on their own F3 screen, which an operator often wants to repeat on a
 * scoreboard or in a join message so both say the same name.
 *
 * <p>Wired during servertweaks wiring; with the module disabled, or the brand tweak switched off, the seam reports
 * nothing and the key degrades to the dash, which is exactly what the client is being told.
 */
public interface ServerTweaksPlaceholders {

    /** The brand sent to clients, or empty when the brand tweak is off and the server's own brand stands. */
    Optional<String> brand();
}
