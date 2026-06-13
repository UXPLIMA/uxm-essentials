package com.uxplima.uxmessentials.vote.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.DurationText;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.SiteCooldown;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.VoteSiteSpec;

/**
 * Shows the player which vote sites they can vote on right now and, for those still on cooldown,
 * how long remains. The catalog drives what is shown; an empty catalog emits a single "no sites"
 * message instead.
 */
public final class ShowNextVote {

    private final VoteRepository repository;
    private final VoteSiteCatalog catalog;
    private final VoteNotifier notifier;

    public ShowNextVote(VoteRepository repository, VoteSiteCatalog catalog, VoteNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Send {@code viewer} the per-site cooldown status evaluated at {@code now}.
     */
    public void show(PlayerRef viewer, Instant now) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(now, "now");

        if (catalog.sites().isEmpty()) {
            notifier.send(viewer, VoteMessageKey.VOTE_NEXT_NONE);
            return;
        }

        notifier.send(viewer, VoteMessageKey.VOTE_NEXT_HEADER);
        for (VoteSiteSpec spec : catalog.sites()) {
            // Cooldown is keyed on the Votifier service (the write key in HandleVote); display uses name.
            SiteCooldown cd =
                    new SiteCooldown(spec.name(), repository.lastVoteAtSite(viewer, spec.service()), spec.cooldown());
            if (cd.isVotable(now)) {
                notifier.send(viewer, VoteMessageKey.VOTE_NEXT_AVAILABLE, Map.of("site", spec.name()));
            } else {
                notifier.send(
                        viewer,
                        VoteMessageKey.VOTE_NEXT_ENTRY,
                        Map.of("site", spec.name(), "time", DurationText.humanize(cd.remaining(now))));
            }
        }
    }
}
