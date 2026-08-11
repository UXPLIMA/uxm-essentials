package com.uxplima.uxmessentials.npc.adapter.outbound.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmNpcQuery;
import com.uxplima.uxmessentials.api.view.UxmNpc;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The published NPC query, over the same repository the {@code /npc} commands read.
 *
 * <p>The store keeps the whole set in memory once warmed, so these reads are cheap; they still go through the
 * scheduler, because the first read after a start or a module reload is the one that warms it from the database
 * and a consumer should not be the thread that finds out.
 *
 * <p>A name no NPC could be called is an absent NPC rather than an exception, which is also what the command says
 * to somebody who typed it.
 */
@NullMarked
public final class NpcQueries implements UxmNpcQuery {

    private final NpcRepository repository;
    private final Scheduler scheduler;

    public NpcQueries(NpcRepository repository, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<List<UxmNpc>> list() {
        return AsyncQueries.supply(
                scheduler, () -> repository.all().stream().map(NpcQueries::view).toList());
    }

    @Override
    public CompletableFuture<Optional<UxmNpc>> get(String name) {
        Objects.requireNonNull(name, "name");
        return AsyncQueries.supply(
                scheduler, () -> parse(name).flatMap(repository::find).map(NpcQueries::view));
    }

    @Override
    public CompletableFuture<Boolean> exists(String name) {
        Objects.requireNonNull(name, "name");
        return AsyncQueries.supply(
                scheduler, () -> parse(name).map(repository::exists).orElse(false));
    }

    @Override
    public CompletableFuture<List<UxmNpc>> ownedBy(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return AsyncQueries.supply(
                scheduler,
                () -> repository.all().stream()
                        .filter(npc -> ownerId.equals(npc.owner()))
                        .map(NpcQueries::view)
                        .toList());
    }

    /** A name as the domain sees it, or empty when nothing could be called that; shared with the write surface. */
    static Optional<NpcName> parse(String name) {
        try {
            return Optional.of(NpcName.of(name));
        } catch (IllegalArgumentException rejected) {
            return Optional.empty();
        }
    }

    /** An NPC as the API publishes it: the shape of it, without the renderer's long tail of knobs. */
    static UxmNpc view(Npc npc) {
        return new UxmNpc(
                npc.name().value(),
                ApiValues.location(npc.location()),
                npc.entityType(),
                npc.hasDisplayName() ? Optional.ofNullable(npc.displayName()) : Optional.empty(),
                npc.displayNameHidden(),
                Optional.ofNullable(npc.clickCommand()),
                npc.actions().size(),
                npc.lookAtPlayer(),
                npc.glowing(),
                npc.hasSkin(),
                Optional.ofNullable(npc.owner()),
                npc.createdAt());
    }
}
