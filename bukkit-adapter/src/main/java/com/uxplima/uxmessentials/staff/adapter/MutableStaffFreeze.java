package com.uxplima.uxmessentials.staff.adapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffFreeze;
import org.jspecify.annotations.NullMarked;

/**
 * A rebindable {@link StaffFreeze} holder: it forwards to {@link StaffFreeze#NONE} until the moderation-backed
 * freeze is bound in, mirroring {@link MutableStaffVanish}. When the moderation module is disabled the binding
 * never happens and the FREEZE gadget stays the no-op {@code NONE}, so the gadget degrades rather than fails.
 */
@NullMarked
public final class MutableStaffFreeze implements StaffFreeze {

    private final AtomicReference<StaffFreeze> delegate = new AtomicReference<>(StaffFreeze.NONE);

    @Override
    public FreezeOutcome toggle(PlayerRef actor, PlayerRef target) {
        return Objects.requireNonNull(delegate.get(), "delegate").toggle(actor, target);
    }

    @Override
    public boolean isFrozen(PlayerRef target) {
        return Objects.requireNonNull(delegate.get(), "delegate").isFrozen(target);
    }

    /** Rebind to the real moderation-provided freeze; called once when the moderation context is enabled. */
    public void bind(StaffFreeze real) {
        delegate.set(Objects.requireNonNull(real, "real"));
    }
}
