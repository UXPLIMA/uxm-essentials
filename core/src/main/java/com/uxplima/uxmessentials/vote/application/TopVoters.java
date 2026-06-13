package com.uxplima.uxmessentials.vote.application;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;

/**
 * Displays the top voters for a given period. The use case queries the repository for the highest
 * {@code limit} players by accumulated vote count and sends a paginated-style header plus one line
 * per ranked player to the viewer. When there are no rows for the period an empty-state message is
 * sent instead.
 *
 * <p>The repository stores the ranked player as a {@link PlayerRef} whose {@code name} may be a
 * UUID-as-string placeholder when the profile was written by an older persistence path. The adapter
 * layer can supply a {@code nameResolver} to translate each UUID to a human-readable name before the
 * entry lines are sent; use {@link #top(PlayerRef, VotePeriod)} when no resolver is available and the
 * stored name is already correct.
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
     * Each ranked player's display name is resolved through {@code nameResolver} so real names appear
     * even when the repository row carries a UUID-as-string placeholder.
     *
     * @param nameResolver a function that accepts a player UUID and returns the best-available display
     *     name (may fall back to the UUID string when the profile is unknown)
     */
    public void top(PlayerRef viewer, VotePeriod period, Function<UUID, String> nameResolver) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(nameResolver, "nameResolver");

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
            String displayName = nameResolver.apply(row.player().uuid());
            notifier.send(
                    viewer,
                    VoteMessageKey.VOTE_TOP_ENTRY,
                    Map.of(
                            "rank", Integer.toString(i + 1),
                            "player", displayName,
                            "votes", Long.toString(row.votes())));
        }
    }

    /**
     * Fetch the top {@link #limit} voters for {@code period} and send the ranking to {@code viewer}.
     * Uses the name stored in the repository row directly (suitable when the persistence layer
     * already resolves real names or when no profile lookup is available).
     */
    public void top(PlayerRef viewer, VotePeriod period) {
        top(viewer, period, uuid -> uuid.toString());
    }
}
