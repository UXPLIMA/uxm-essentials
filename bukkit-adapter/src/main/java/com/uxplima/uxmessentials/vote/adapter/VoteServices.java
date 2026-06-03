package com.uxplima.uxmessentials.vote.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.vote.application.ApplyQueuedRewards;
import com.uxplima.uxmessentials.vote.application.HandleVote;
import com.uxplima.uxmessentials.vote.application.VoteLinks;
import com.uxplima.uxmessentials.vote.application.VotePartyStatus;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed vote use cases the Brigadier commands and listeners share, built once per module start
 * by {@code VoteWiring} from the kernel ports, the cached jOOQ repository, the reward dispatcher, and the
 * online audience. Held so every inbound adapter reads the same use cases; the {@link Scheduler} and
 * {@link Messages} ports are kept here too because the listeners hop the vote handling onto a tick thread
 * and the commands render their replies in the viewer's locale.
 *
 * @param handleVote the core use case the Votifier listener and {@code /vote testreward} drive
 * @param applyQueuedRewards the join handler that pays out an offline voter's queued rewards
 * @param voteLinks the {@code /vote} display of the configured vote links
 * @param votePartyStatus the {@code /voteparty} party-progress display
 * @param scheduler the Folia-aware scheduler the listeners hop the work onto
 * @param messages MessageKey resolution for the command replies
 */
@NullMarked
public record VoteServices(
        HandleVote handleVote,
        ApplyQueuedRewards applyQueuedRewards,
        VoteLinks voteLinks,
        VotePartyStatus votePartyStatus,
        Scheduler scheduler,
        Messages messages) {

    public VoteServices {
        Objects.requireNonNull(handleVote, "handleVote");
        Objects.requireNonNull(applyQueuedRewards, "applyQueuedRewards");
        Objects.requireNonNull(voteLinks, "voteLinks");
        Objects.requireNonNull(votePartyStatus, "votePartyStatus");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(messages, "messages");
    }
}
