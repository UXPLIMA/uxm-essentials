/**
 * The vote context's outbound ports: {@code VoteRepository} for the durable party counter and the
 * per-player offline reward queue; {@code RewardApplier} for applying one resolved {@code RewardGrant}
 * (dispatch/queue commands, deliver messages, broadcasts, and items); {@code VoteContext} for the
 * boundary facts the reward engine needs (the voter's world, permissions, online state, and chance roll);
 * {@code RewardDispatcher} for running drained offline-reward command batches with the {@code {player}}
 * substitution; and {@code VoteAudience} for the set of online players a party rewards and the thank-you
 * broadcasts to. The application depends only on these interfaces; the jOOQ repository and the Bukkit
 * applier/context/dispatcher/audience implement them in the persistence and bukkit adapters.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vote.application.port;
