package com.uxplima.uxmessentials.vote.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.vote.application.AddPartyCount;
import com.uxplima.uxmessentials.vote.application.ApplyQueuedRewards;
import com.uxplima.uxmessentials.vote.application.ForceParty;
import com.uxplima.uxmessentials.vote.application.GiveVote;
import com.uxplima.uxmessentials.vote.application.HandleVote;
import com.uxplima.uxmessentials.vote.application.ResetVoterTotals;
import com.uxplima.uxmessentials.vote.application.SetPartyCount;
import com.uxplima.uxmessentials.vote.application.ShowLastVote;
import com.uxplima.uxmessentials.vote.application.ShowNextVote;
import com.uxplima.uxmessentials.vote.application.ShowVoteTotals;
import com.uxplima.uxmessentials.vote.application.TopVoters;
import com.uxplima.uxmessentials.vote.application.VoteLinks;
import com.uxplima.uxmessentials.vote.application.VotePartyStatus;
import com.uxplima.uxmessentials.vote.application.VoteReminderEligibility;
import com.uxplima.uxmessentials.vote.application.port.ReminderPreferences;
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
 * @param showVoteTotals the {@code /vote total [player]} per-player tally display
 * @param topVoters the {@code /vote top [period]} leaderboard display
 * @param showNextVote the {@code /vote next} per-site cooldown display
 * @param showLastVote the {@code /vote last} per-site last-vote display
 * @param reminderEligibility determines if a player has at least one site ready to vote on
 * @param reminderPreferences per-player opt-in/out preference (PDC-backed)
 * @param forceParty admin: fire the party immediately
 * @param setPartyCount admin: set the counter to an exact value
 * @param addPartyCount admin: add to the counter (fires if threshold reached)
 * @param giveVote admin: inject synthetic votes for a player
 * @param resetVoterTotals admin: clear a player's vote totals
 * @param playerLookup offline-capable profile resolution for target arguments and leaderboard names
 * @param scheduler the Folia-aware scheduler the listeners hop the work onto
 * @param messages MessageKey resolution for the command replies
 */
@NullMarked
public record VoteServices(
        HandleVote handleVote,
        ApplyQueuedRewards applyQueuedRewards,
        VoteLinks voteLinks,
        VotePartyStatus votePartyStatus,
        ShowVoteTotals showVoteTotals,
        TopVoters topVoters,
        ShowNextVote showNextVote,
        ShowLastVote showLastVote,
        VoteReminderEligibility reminderEligibility,
        ReminderPreferences reminderPreferences,
        ForceParty forceParty,
        SetPartyCount setPartyCount,
        AddPartyCount addPartyCount,
        GiveVote giveVote,
        ResetVoterTotals resetVoterTotals,
        PlayerLookup playerLookup,
        Scheduler scheduler,
        Messages messages) {

    public VoteServices {
        Objects.requireNonNull(handleVote, "handleVote");
        Objects.requireNonNull(applyQueuedRewards, "applyQueuedRewards");
        Objects.requireNonNull(voteLinks, "voteLinks");
        Objects.requireNonNull(votePartyStatus, "votePartyStatus");
        Objects.requireNonNull(showVoteTotals, "showVoteTotals");
        Objects.requireNonNull(topVoters, "topVoters");
        Objects.requireNonNull(showNextVote, "showNextVote");
        Objects.requireNonNull(showLastVote, "showLastVote");
        Objects.requireNonNull(reminderEligibility, "reminderEligibility");
        Objects.requireNonNull(reminderPreferences, "reminderPreferences");
        Objects.requireNonNull(forceParty, "forceParty");
        Objects.requireNonNull(setPartyCount, "setPartyCount");
        Objects.requireNonNull(addPartyCount, "addPartyCount");
        Objects.requireNonNull(giveVote, "giveVote");
        Objects.requireNonNull(resetVoterTotals, "resetVoterTotals");
        Objects.requireNonNull(playerLookup, "playerLookup");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(messages, "messages");
    }
}
