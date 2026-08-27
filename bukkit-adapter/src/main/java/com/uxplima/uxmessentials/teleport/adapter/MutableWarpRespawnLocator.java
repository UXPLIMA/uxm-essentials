package com.uxplima.uxmessentials.teleport.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.application.port.WarpRespawnLocator;

/** Late-bound warp lookup: empty until the optional warps module has wired its warmed repository. */
public final class MutableWarpRespawnLocator implements WarpRespawnLocator {

    private static final WarpRespawnLocator NONE = name -> Optional.empty();
    private final AtomicReference<WarpRespawnLocator> delegate = new AtomicReference<>(NONE);

    @Override
    public Optional<Position> respawnWarp(String name) {
        return Objects.requireNonNull(delegate.get(), "delegate").respawnWarp(name);
    }

    public void bind(WarpRespawnLocator real) {
        delegate.set(Objects.requireNonNull(real, "real"));
    }
}
