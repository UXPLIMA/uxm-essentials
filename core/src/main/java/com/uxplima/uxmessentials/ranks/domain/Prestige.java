package com.uxplima.uxmessentials.ranks.domain;

/**
 * A player's prestige level — how many times they have reset the ladder from the top back to the first rank. It
 * starts at {@link #INITIAL zero} for a player who has never prestiged and only ever increases. The level is
 * persisted in {@code player_ranks.prestige}; the eligibility and reward rules that drive an increment land in
 * the prestige phase, so here it is a plain non-negative counter with the one legal transition — {@link
 * #increment()}.
 *
 * @param level the prestige count, never negative
 */
public record Prestige(int level) {

    /** The prestige of a player who has never prestiged. */
    public static final Prestige INITIAL = new Prestige(0);

    public Prestige {
        if (level < 0) {
            throw new IllegalArgumentException("prestige level must not be negative: " + level);
        }
    }

    /** The next prestige level up. */
    public Prestige increment() {
        return new Prestige(level + 1);
    }

    /** Whether the player has never prestiged. */
    public boolean isInitial() {
        return level == 0;
    }
}
