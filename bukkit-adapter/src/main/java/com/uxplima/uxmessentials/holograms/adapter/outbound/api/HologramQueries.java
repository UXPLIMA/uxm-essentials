package com.uxplima.uxmessentials.holograms.adapter.outbound.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmHologramsQuery;
import com.uxplima.uxmessentials.api.view.UxmHologram;
import com.uxplima.uxmessentials.api.view.UxmHologramType;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.HologramType;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The published hologram query, over the same repository the {@code /hologram} commands read.
 *
 * <p>The lines come back as stored. A hologram line may carry MiniMessage and placeholders, both of which resolve
 * per viewer at render time, so there is no one rendered string to hand over: publishing the source is the only
 * answer that is the same for everybody asking.
 */
@NullMarked
public final class HologramQueries implements UxmHologramsQuery {

    private final HologramRepository repository;
    private final Scheduler scheduler;

    public HologramQueries(HologramRepository repository, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<List<UxmHologram>> list() {
        return AsyncQueries.supply(
                scheduler,
                () -> repository.all().stream().map(HologramQueries::view).toList());
    }

    @Override
    public CompletableFuture<Optional<UxmHologram>> get(String name) {
        Objects.requireNonNull(name, "name");
        return AsyncQueries.supply(
                scheduler, () -> parse(name).flatMap(repository::find).map(HologramQueries::view));
    }

    @Override
    public CompletableFuture<Boolean> exists(String name) {
        Objects.requireNonNull(name, "name");
        return AsyncQueries.supply(
                scheduler, () -> parse(name).map(repository::exists).orElse(false));
    }

    /** A name as the domain sees it, or empty when nothing could be called that; shared with the write surface. */
    static Optional<HologramName> parse(String name) {
        try {
            return Optional.of(HologramName.of(name));
        } catch (IllegalArgumentException rejected) {
            return Optional.empty();
        }
    }

    /** A hologram as the API publishes it. */
    static UxmHologram view(Hologram hologram) {
        return new UxmHologram(
                hologram.name().value(),
                ApiValues.location(hologram.location()),
                type(hologram.type()),
                hologram.lines().stream().map(HologramLine::value).toList(),
                content(hologram),
                Optional.ofNullable(hologram.linkedNpcName()),
                Optional.ofNullable(hologram.clickCommand()),
                hologram.actions().size(),
                hologram.pageCount(),
                hologram.refreshIntervalTicks(),
                hologram.createdAt());
    }

    /** The one thing a non-text hologram is made of, whichever field the type keeps it in. */
    private static Optional<String> content(Hologram hologram) {
        return switch (hologram.type()) {
            case TEXT -> Optional.empty();
            case ITEM -> Optional.ofNullable(hologram.itemMaterial());
            case BLOCK -> Optional.ofNullable(hologram.blockData());
            case HEAD -> Optional.ofNullable(hologram.headTexture());
            case ENTITY -> Optional.ofNullable(hologram.entityType());
        };
    }

    private static UxmHologramType type(HologramType type) {
        return switch (type) {
            case TEXT -> UxmHologramType.TEXT;
            case ITEM -> UxmHologramType.ITEM;
            case BLOCK -> UxmHologramType.BLOCK;
            case HEAD -> UxmHologramType.HEAD;
            case ENTITY -> UxmHologramType.ENTITY;
        };
    }
}
