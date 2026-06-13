package com.uxplima.uxmessentials.vote.application;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;

/**
 * Displays the top voters for a given period. The use case queries the repository for the highest
 * {@code limit} players by accumulated vote count and sends a paginated-style header plus one line
 * per ranked player to the viewer. When there are no rows for the period an empty-state message is
 * sent instead.
 */
public final class TopVoters {

    private final VoteRepository repository;
    private final VoteNotifier notifier;
    private final int limit;

    public TopVoters(VoteRepository repository, VoteNotifier notifier, int limit) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least one: " + limit);
        }
        this.limit = limit;
    }

    /**
     * Fetch the top {@link #limit} voters for {@code period} and send the ranking to {@code viewer}.
     */
    public void top(PlayerRef viewer, VotePeriod period) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(period, "period");

        List<VoteRanking> rows = repository.topVoters(period, limit);
        if (rows.isEmpty()) {
            notifier.send(viewer, VoteMessageKey.VOTE_TOP_EMPTY);
            return;
        }

        notifier.send(
                viewer,
                VoteMessageKey.VOTE_TOP_HEADER,
                Map.of(
                        "period", period.name().toLowerCase(Locale.ROOT),
                        "count", Integer.toString(rows.size())));

        for (int i = 0; i < rows.size(); i++) {
            VoteRanking row = rows.get(i);
            notifier.send(
                    viewer,
                    VoteMessageKey.VOTE_TOP_ENTRY,
                    Map.of(
                            "rank", Integer.toString(i + 1),
                            "player", row.player().name(),
                            "votes", Long.toString(row.votes())));
        }
    }
}
