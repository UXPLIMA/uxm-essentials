package com.uxplima.uxmessentials.teleport.adapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.RequestVisibility;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link RequestVisibility} the vanish context rebinds when it wires, after teleport. Until then, and for good when
 * vanish is off, nobody is hidden. The reference is atomic so the rebind on the enable thread is visible to the
 * threads a cross-server request is judged on.
 */
@NullMarked
public final class MutableRequestVisibility implements RequestVisibility {

    private final AtomicReference<RequestVisibility> delegate = new AtomicReference<>(RequestVisibility.EVERYONE);

    @Override
    public int seeLevel(PlayerRef viewer) {
        return Objects.requireNonNull(delegate.get(), "delegate").seeLevel(viewer);
    }

    @Override
    public boolean hides(PlayerRef target, PlayerRef viewer, int viewerSeeLevel) {
        return Objects.requireNonNull(delegate.get(), "delegate").hides(target, viewer, viewerSeeLevel);
    }

    /** Rebind to the vanish authority; called once when the vanish context wires. */
    public void bind(RequestVisibility real) {
        delegate.set(Objects.requireNonNull(real, "real"));
    }
}
