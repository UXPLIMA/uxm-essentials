package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import org.jspecify.annotations.NullMarked;

/**
 * {@link HologramsPlaceholders} over the holograms context's {@link HologramRepository}. Built during holograms
 * wiring from the same cached repository the {@code /hologram} use cases hold, so the count matches what {@code
 * /hologram list} shows.
 *
 * <p>The repository is the Caffeine-cached jOOQ adapter, so {@link HologramRepository#all()} is a cheap cache
 * read (the full set is loaded once and invalidated on any write); taking its size is safe on the placeholder
 * path.
 */
@NullMarked
public final class RepositoryHologramsPlaceholders implements HologramsPlaceholders {

    private final HologramRepository repository;

    public RepositoryHologramsPlaceholders(HologramRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public int count() {
        return repository.all().size();
    }
}
