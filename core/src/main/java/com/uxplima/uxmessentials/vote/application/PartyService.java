package com.uxplima.uxmessentials.vote.application;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;

/**
 * The shared party-fire logic: determine eligible recipients, apply the party reward to each, publish
 * {@link VotePartyTriggered}, reset the counter and participant set, and escalate the threshold when
 * configured. Both {@link HandleVote} and {@link ForceParty} delegate here so the fire path is never
 * duplicated.
 */
final class PartyService {

    private final VoteRepository repository;
    private final RewardApplier applier;
    private final VoteAudience audience;
    private final VoteNotifier notifier;
    private final DomainEventPublisher events;
    private final PartyConfig party;

    PartyService(
            VoteRepository repository,
            RewardApplier applier,
            VoteAudience audience,
            VoteNotifier notifier,
            DomainEventPublisher events,
            PartyConfig party) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.applier = Objects.requireNonNull(applier, "applier");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.party = Objects.requireNonNull(party, "party");
    }

    /**
     * Fire the party at the given effective threshold: pay the party reward to every eligible online
     * player, publish the event, reset the counter and participant set, and escalate the threshold if
     * configured.
     *
     * @param effectiveThreshold the threshold at which this party fires (used in the event and notifications)
     */
    void fire(int effectiveThreshold) {
        RewardGrant partyGrant = RewardGrant.of(party.reward());
        Collection<PlayerRef> online = audience.online();
        Set<UUID> participants = party.onlyVoters() ? repository.partyParticipants() : null;

        for (PlayerRef player : online) {
            if (participants == null || participants.contains(player.uuid())) {
                applier.apply(player, true, partyGrant);
                notifier.send(
                        player,
                        VoteMessageKey.VOTEPARTY_REACHED,
                        Map.of("threshold", Integer.toString(effectiveThreshold)));
            }
        }

        events.publish(new VotePartyTriggered(effectiveThreshold));
        repository.clearPartyParticipants();
        repository.setPartyCount(0);

        if (party.escalateBy() > 0) {
            repository.setThresholdOverride(effectiveThreshold + party.escalateBy());
        }
    }

    /**
     * The current effective threshold: the stored override when positive, otherwise the base threshold
     * from config.
     */
    int effectiveThreshold() {
        int override = repository.thresholdOverride();
        return override > 0 ? override : party.baseThreshold();
    }
}
