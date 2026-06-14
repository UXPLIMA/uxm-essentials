package com.uxplima.uxmessentials.vote.application;

import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;

/**
 * Admin use case: fire the vote party immediately, regardless of the current counter value. Uses
 * {@link PartyService} so the fire path is identical to the automatic fire in {@link HandleVote}.
 */
public final class ForceParty {

    private final VoteRepository repository;
    private final PartyService partyService;
    private final VoteAudience audience;
    private final VoteNotifier notifier;

    public ForceParty(
            VoteRepository repository,
            RewardApplier applier,
            VoteAudience audience,
            VoteNotifier notifier,
            VoteBroadcaster broadcaster,
            Set<BroadcastChannel> channels,
            DomainEventPublisher events,
            PartyConfig party) {
        this.repository = Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(applier, "applier");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(broadcaster, "broadcaster");
        Objects.requireNonNull(channels, "channels");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(party, "party");
        this.partyService = new PartyService(repository, applier, audience, broadcaster, channels, events, party);
    }

    /**
     * Force the party to fire immediately, regardless of the current counter value. The counter is
     * reset to zero before firing so the reset ownership model is consistent: PartyService.fire never
     * resets the counter itself, each call site is responsible.
     */
    public void execute(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        int threshold = partyService.effectiveThreshold();
        repository.setPartyCount(0);
        partyService.fire(threshold);
        notifier.send(actor, VoteMessageKey.VOTEPARTY_FORCED);
        for (PlayerRef online : audience.online()) {
            notifier.send(online, VoteMessageKey.VOTEPARTY_FORCED);
        }
    }
}
