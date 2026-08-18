package com.uxplima.uxmessentials.skin.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmSkinQuery;
import com.uxplima.uxmessentials.api.view.UxmSkin;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import org.jspecify.annotations.NullMarked;

/**
 * The published skin read, over the same repository {@code /skin info} reads.
 *
 * <p>A database read behind a cache, so it goes to a worker like every other published query. The texture is left
 * behind: it is the bulk of the row and means nothing outside a client.
 */
@NullMarked
public final class SkinQueries implements UxmSkinQuery {

    private final SkinRepository repository;
    private final Scheduler scheduler;

    public SkinQueries(SkinRepository repository, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<Optional<UxmSkin>> of(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> repository.find(playerId).map(SkinQueries::view));
    }

    private static UxmSkin view(PlayerSkin skin) {
        return new UxmSkin(
                SkinSources.typeOf(skin.source()),
                skin.source().value(),
                skin.model() == SkinModel.SLIM,
                skin.appliedAt());
    }
}
