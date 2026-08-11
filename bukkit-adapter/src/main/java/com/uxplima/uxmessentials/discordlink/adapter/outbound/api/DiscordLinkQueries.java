package com.uxplima.uxmessentials.discordlink.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmDiscordLinkQuery;
import com.uxplima.uxmessentials.api.view.UxmDiscordLink;
import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The published binding query, over the same store {@code /discordlink status} and the {@code /link} slash
 * command read.
 *
 * <p>Every answer is a database row, so every one goes to a worker. Both directions are indexed, so neither is
 * the slow one.
 */
@NullMarked
public final class DiscordLinkQueries implements UxmDiscordLinkQuery {

    private final DiscordLinkStore store;
    private final Scheduler scheduler;

    public DiscordLinkQueries(DiscordLinkStore store, Scheduler scheduler) {
        this.store = Objects.requireNonNull(store, "store");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<Optional<UxmDiscordLink>> of(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> store.findByPlayer(playerId).map(DiscordLinkQueries::view));
    }

    @Override
    public CompletableFuture<Optional<UxmDiscordLink>> byDiscordId(String discordId) {
        Objects.requireNonNull(discordId, "discordId");
        Optional<DiscordId> id = parse(discordId);
        if (id.isEmpty()) {
            // Not a snowflake, so nothing is bound to it. Answering "nobody" is true and keeps a mistyped id from
            // reaching the caller as a stack trace.
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return AsyncQueries.supply(
                scheduler, () -> store.findByDiscordId(id.orElseThrow()).map(DiscordLinkQueries::view));
    }

    @Override
    public CompletableFuture<Boolean> isLinked(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> store.findByPlayer(playerId).isPresent());
    }

    private static Optional<DiscordId> parse(String discordId) {
        try {
            return Optional.of(DiscordId.of(discordId));
        } catch (IllegalArgumentException notASnowflake) {
            return Optional.empty();
        }
    }

    private static UxmDiscordLink view(ConfirmedLink link) {
        return new UxmDiscordLink(link.player(), link.discordId().value(), link.linkedAt());
    }
}
