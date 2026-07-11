package com.uxplima.uxmessentials.migration.convert.ax.parse;

/**
 * One row of {@code axplayerwarps_ratings}: a single reviewer's star vote on a warp. The {@code reviewerId} is
 * resolved to a uuid through the player map; {@code stars} is the 1..5 value the source stored, and {@code date} is
 * the epoch-millisecond instant the vote was cast ({@code 0} when the source stored none).
 *
 * @param warpId the rated warp's id
 * @param reviewerId the reviewer's AxPlayerWarps player id
 * @param stars the star value
 * @param date when the vote was cast, in epoch milliseconds
 */
public record AxRatingRow(int warpId, int reviewerId, int stars, long date) {}
