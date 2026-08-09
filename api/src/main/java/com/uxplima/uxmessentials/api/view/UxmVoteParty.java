package com.uxplima.uxmessentials.api.view;

/**
 * How close the server is to its next vote party.
 *
 * <p>The threshold is the one in force now, which is not always the configured one: an operator can raise the bar
 * for the current round, and this reports the raised figure because that is what the players are counting towards.
 *
 * @param count votes accumulated towards the next party
 * @param threshold votes the party fires at
 */
public record UxmVoteParty(int count, int threshold) {

    public UxmVoteParty {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be at least one: " + threshold);
        }
    }

    /** How many more votes the server needs, never below zero. */
    public int remaining() {
        return Math.max(0, threshold - count);
    }
}
