package com.uxplima.uxmessentials.warps.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.application.port.WarpRespawnLocator;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/** Resolves respawn {@code warp:<name>} steps through the warps module's already-warmed repository. */
public final class RepositoryWarpRespawnLocator implements WarpRespawnLocator {

    private final WarpRepository repository;

    public RepositoryWarpRespawnLocator(WarpRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<Position> respawnWarp(String name) {
        try {
            return repository.find(WarpName.of(name)).map(Warp::location);
        } catch (IllegalArgumentException invalidName) {
            return Optional.empty();
        }
    }
}
