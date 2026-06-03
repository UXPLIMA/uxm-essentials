/**
 * The vote context's outbound ports: {@code VoteRepository} for the durable party counter and the
 * per-player offline reward queue, {@code RewardDispatcher} for running reward console commands with the
 * {@code {player}} substitution, and {@code VoteAudience} for the set of online players a party rewards
 * and the thank-you broadcasts to. The application depends only on these interfaces; the jOOQ
 * repository and the Bukkit dispatcher/audience implement them in the persistence and bukkit adapters.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vote.application.port;
