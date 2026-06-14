package com.uxplima.uxmessentials.staff.adapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffInspector;
import org.jspecify.annotations.NullMarked;

/**
 * A rebindable {@link StaffInspector} holder: it forwards to {@link StaffInspector#NONE} until the
 * playerstate-backed inspector is bound in, mirroring the messaging context's {@code MutableMutePolicy}. When
 * the playerstate module is disabled the binding never happens and the EXAMINE gadget's inventory open stays
 * the no-op {@code NONE} (the listener still shows the info line regardless), so the gadget degrades rather
 * than fails.
 */
@NullMarked
public final class MutableStaffInspector implements StaffInspector {

    private final AtomicReference<StaffInspector> delegate = new AtomicReference<>(StaffInspector.NONE);

    @Override
    public void inspect(PlayerRef looker, PlayerRef target) {
        Objects.requireNonNull(delegate.get(), "delegate").inspect(looker, target);
    }

    /** Rebind to the real playerstate-provided inspector; called once when the playerstate context is enabled. */
    public void bind(StaffInspector real) {
        delegate.set(Objects.requireNonNull(real, "real"));
    }
}
