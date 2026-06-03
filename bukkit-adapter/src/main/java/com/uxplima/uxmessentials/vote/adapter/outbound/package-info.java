/**
 * The vote context's outbound adapters: {@code BukkitRewardDispatcher} runs reward console commands from
 * the console sender on the global region thread (with the {@code {player}} substitution), and
 * {@code BukkitVoteAudience} snapshots the online players as kernel {@code PlayerRef}s for the party
 * rewards and thank-you broadcast. Both translate between Bukkit handles and the kernel value objects
 * behind the vote context's application ports.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vote.adapter.outbound;
