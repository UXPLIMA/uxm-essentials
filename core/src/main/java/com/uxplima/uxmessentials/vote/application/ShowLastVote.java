package com.uxplima.uxmessentials.vote.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.DurationText;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.VoteSiteSpec;

/**
 * Shows the player when they last voted on each configured site. Sites they have never voted on
 * show a "never" entry; sites with a recorded timestamp show elapsed time since then.
 */
public final class ShowLastVote {

    private final VoteRepository repository;
    private final VoteSiteCatalog catalog;
    private final VoteNotifier notifier;

    public ShowLastVote(VoteRepository repository, VoteSiteCatalog catalog, VoteNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Send {@code viewer} the last-vote summary for each site, evaluated at {@code now}.
     */
    public void show(PlayerRef viewer, Instant now) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(now, "now");

        if (catalog.sites().isEmpty()) {
            notifier.send(viewer, VoteMessageKey.VOTE_NEXT_NONE);
            return;
        }

        notifier.send(viewer, VoteMessageKey.VOTE_LAST_HEADER);
        for (VoteSiteSpec spec : catalog.sites()) {
            // Cooldown is keyed on the Votifier service (the write key in HandleVote); display uses name.
            Optional<Instant> last = repository.lastVoteAtSite(viewer, spec.service());
            if (last.isEmpty()) {
                notifier.send(viewer, VoteMessageKey.VOTE_LAST_NEVER, Map.of("site", spec.name()));
            } else {
                Duration elapsed = Duration.between(last.get(), now);
                notifier.send(
                        viewer,
                        VoteMessageKey.VOTE_LAST_ENTRY,
                        Map.of("site", spec.name(), "time", DurationText.humanize(elapsed)));
            }
        }
    }
}
