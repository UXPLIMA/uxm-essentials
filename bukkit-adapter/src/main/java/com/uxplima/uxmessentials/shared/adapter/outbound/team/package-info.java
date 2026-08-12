/**
 * The shared scoreboard-team seam three contexts lean on:
 * {@link com.uxplima.uxmessentials.shared.adapter.outbound.team.PlayerTeamCoordinator} owns which team a player sits
 * in, deriving it from the two vanilla behaviours that team membership carries: the hidden above-head name a custom
 * nametag needs, and the colour of a {@code /glow} outline. A player can only belong to one team per board, so both
 * have to be decided in one place. It lives in the shared adapter layer because it touches the Bukkit
 * {@code Team}/{@code Scoreboard} API directly while serving the nametags, scoreboard and playerstate contexts.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.team;

import org.jspecify.annotations.NullMarked;
