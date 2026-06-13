package com.uxplima.uxmessentials.vote.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;

/**
 * Admin use case: fire the vote party immediately, regardless of the current counter value. Uses
 * {@link PartyService} so the fire path is identical to the automatic fire in {@link HandleVote}.
 */
public final class ForceParty {

    private final PartyService partyService;
    private final VoteAudience audience;
    private final VoteNotifier notifier;

    public ForceParty(
            VoteRepository repository,
            RewardApplier applier,
            VoteAudience audience,
            VoteNotifier notifier,
            DomainEventPublisher events,
            PartyConfig party) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(applier, "applier");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(party, "party");
        this.partyService = new PartyService(repository, applier, audience, notifier, events, party);
    }

    /**
     * Force the party to fire immediately. The effective threshold at the moment of forcing is used
     * for the event and notification, then the counter resets and escalation applies as normal.
     */
    public void execute(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        int threshold = partyService.effectiveThreshold();
        partyService.fire(threshold);
        notifier.send(actor, VoteMessageKey.VOTEPARTY_FORCED);
        for (PlayerRef online : audience.online()) {
            notifier.send(online, VoteMessageKey.VOTEPARTY_FORCED);
        }
    }
}
