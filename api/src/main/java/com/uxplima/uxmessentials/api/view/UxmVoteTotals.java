package com.uxplima.uxmessentials.api.view;

/**
 * How much a player has voted.
 *
 * <p>The three periodic counts cover the calendar window they name and reset when it turns over, so a daily count
 * of zero means they have not voted since the server's midnight rather than that they have never voted. The streak
 * counts consecutive days: voting twice in one day does not extend it, and missing a day ends it.
 *
 * @param allTime every vote they have ever cast on this server
 * @param daily votes cast on the server's current day
 * @param weekly votes cast in the current week
 * @param monthly votes cast in the current month
 * @param currentStreak the run of consecutive days they have voted on
 * @param bestStreak the longest such run they have ever reached
 */
public record UxmVoteTotals(long allTime, long daily, long weekly, long monthly, long currentStreak, long bestStreak) {

    /** Everything at zero, which is what a player who has never voted reads as. */
    public static UxmVoteTotals empty() {
        return new UxmVoteTotals(0L, 0L, 0L, 0L, 0L, 0L);
    }

    /** Whether this player has ever voted on the server. */
    public boolean hasVoted() {
        return allTime > 0L;
    }
}
