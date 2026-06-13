package com.uxplima.uxmessentials.vote.application;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.BroadcastChannel;

/**
 * Admin use case: add a number of votes to the global party counter and fire the party if the new
 * count reaches the effective threshold. The fire path delegates to {@link PartyService} so it is
 * identical to the automatic fire in {@link HandleVote}.
 */
public final class AddPartyCount {

    private final VoteRepository repository;
    private final PartyService partyService;
    private final VoteNotifier notifier;

    public AddPartyCount(
            VoteRepository repository,
            RewardApplier applier,
            VoteAudience audience,
            VoteNotifier notifier,
            VoteBroadcaster broadcaster,
            Set<BroadcastChannel> channels,
            DomainEventPublisher events,
            PartyConfig party) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(applier, "applier");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(broadcaster, "broadcaster");
        Objects.requireNonNull(channels, "channels");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(party, "party");
        this.partyService = new PartyService(repository, applier, audience, broadcaster, channels, events, party);
    }

    /**
     * Add {@code amount} to the counter and fire a party if the threshold is now reached. Notifies
     * {@code actor} of the new count (or the party fire).
     *
     * <p>When the new count would reach the threshold the counter is written first, then
     * {@link VoteRepository#claimPartyFire} atomically claims the reset so the logic stays
     * consistent with the {@link HandleVote} path (only one caller fires even if two admin commands
     * race, which is unlikely but guarded for correctness).
     *
     * @param actor  the admin running the command
     * @param amount the number of votes to add (must be positive)
     */
    public void add(PlayerRef actor, int amount) {
        Objects.requireNonNull(actor, "actor");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least one: " + amount);
        }
        int newCount = repository.partyCount() + amount;
        int threshold = partyService.effectiveThreshold();
        if (newCount >= threshold) {
            repository.setPartyCount(newCount);
            if (repository.claimPartyFire(threshold)) {
                partyService.fire(threshold);
            }
        } else {
            repository.setPartyCount(newCount);
            notifier.send(actor, VoteMessageKey.VOTEPARTY_COUNT_SET, Map.of("count", Integer.toString(newCount)));
        }
    }
}
