package com.uxplima.uxmessentials.holograms.application;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.domain.LocalServer;

/**
 * The view every hologram render goes through, so a hologram scoped to other servers never shows here. A render of
 * a hologram this server does not cover takes it off instead: a scope changed here or on a peer leaves the world on
 * the render of the changed snapshot.
 */
public final class ScopedHologramView implements HologramView {

    private final HologramView delegate;
    private final LocalServer local;

    public ScopedHologramView(HologramView delegate, LocalServer local) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.local = Objects.requireNonNull(local, "local");
    }

    @Override
    public void render(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        if (hologram.shownOn(local)) {
            delegate.render(hologram);
        } else {
            delegate.despawn(hologram.name());
        }
    }

    @Override
    public void despawn(HologramName name) {
        delegate.despawn(name);
    }

    @Override
    public void applyManualViewer(HologramName name, UUID viewer, boolean visible) {
        delegate.applyManualViewer(name, viewer, visible);
    }
}
