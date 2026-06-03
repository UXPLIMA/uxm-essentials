/**
 * Pure domain of the vote bounded context: the {@code Vote} value object (voter, service, time), the
 * {@code VotePartyCounter} (accumulated count against a threshold, with increment/reached/reset
 * arithmetic), the {@code QueuedReward} batch held for an offline voter, and the sealed
 * {@code VoteEvent} family ({@code VoteReceived}, {@code VotePartyTriggered}). No Bukkit, Paper, Kyori,
 * or logging type appears here; the model is built from value objects and the kernel primitive
 * {@code PlayerRef}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vote.domain;
