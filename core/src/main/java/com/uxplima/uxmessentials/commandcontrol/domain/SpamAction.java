package com.uxplima.uxmessentials.commandcontrol.domain;

import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * What the command-spam guard does to a player who sends more commands than the configured window allows.
 *
 * <ul>
 *   <li>{@link #KICK} - disconnect the player with the spam message (the harshest response, for a bot / macro flood).
 *   <li>{@link #BLOCK} - cancel only the offending command and warn the player, leaving them connected (the default:
 *       stops the flood from reaching the dispatcher without disconnecting a human who merely mashed a key).
 *   <li>{@link #WARN} - let the command run but still tell the player they are going too fast (an observe-and-nudge
 *       mode that never interferes with a command, useful while tuning the threshold).
 * </ul>
 *
 * <p>The action is chosen once in config and is the same for every player; the bypass permission exempts a holder from
 * the count entirely, so no action ever fires for them.
 */
public enum SpamAction {
    KICK,
    BLOCK,
    WARN;

    /**
     * Parse the config {@code action} string case-insensitively, returning {@code fallback} for a blank or unrecognised
     * value so a typo never silently escalates a warn into a kick.
     */
    public static SpamAction fromConfig(@Nullable String raw, SpamAction fallback) {
        Objects.requireNonNull(fallback, "fallback");
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "kick" -> KICK;
            case "block" -> BLOCK;
            case "warn" -> WARN;
            default -> fallback;
        };
    }
}
