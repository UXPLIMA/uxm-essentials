package com.uxplima.uxmessentials.api.view;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The switches uxmEssentials holds for an online player: god mode, flight, their speeds, and the game mode it is
 * keeping them in.
 *
 * <p>This is what the plugin set, not what the server currently reports. The two agree while nothing else is
 * fighting over the player, and where they differ this is the intent: another plugin can change somebody's game
 * mode out from under a {@code /gamemode} the operator ran, and uxmEssentials would still be holding the mode it
 * was told to hold.
 *
 * <p>The speeds are the multipliers the player experiences, which is the number Bukkit takes. The game mode is
 * empty when the plugin is not pinning one.
 *
 * @param playerId the player
 * @param godMode whether they are taking no damage
 * @param flying whether flight is switched on for them
 * @param gameMode the mode the plugin is holding them in, or empty when it holds none
 * @param walkSpeed their walk speed multiplier
 * @param flySpeed their fly speed multiplier
 */
public record UxmPlayerState(
        UUID playerId,
        boolean godMode,
        boolean flying,
        Optional<UxmGameMode> gameMode,
        float walkSpeed,
        float flySpeed) {

    public UxmPlayerState {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(gameMode, "gameMode");
    }
}
