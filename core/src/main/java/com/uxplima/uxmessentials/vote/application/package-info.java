/**
 * Application layer of the vote bounded context: the {@code VoteModule} feature-module declaration, the
 * {@code HandleVote} core use case (record the tally, resolve the reward sets through the
 * {@code RewardEngine} and apply each {@code RewardGrant}, advance the party counter, fire a party at the
 * threshold), the {@code RewardEngine} (pure resolution of per-vote/per-site/first-vote/milestone reward
 * sets against a {@code RewardContext}), {@code ApplyQueuedRewards} (pay an offline voter on join),
 * {@code VoteLinks} and
 * {@code VotePartyStatus} (the {@code /vote} and {@code /voteparty} displays), the {@code VoteNotifier}
 * (MessageKey resolution + delivery), the {@code VoteCommandSurface} (platform-neutral command specs),
 * and the {@code VoteMessageKey} catalog. Use cases orchestrate the domain through the outbound ports in
 * {@code application/port}; no Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vote.application;
