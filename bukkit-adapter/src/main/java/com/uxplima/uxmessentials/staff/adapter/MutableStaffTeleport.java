package com.uxplima.uxmessentials.staff.adapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffTeleport;
import org.jspecify.annotations.NullMarked;

/**
 * A rebindable {@link StaffTeleport} holder: it forwards to {@link StaffTeleport#NONE} until the teleport-backed
 * teleport is bound in, mirroring {@link MutableStaffVanish}. When the teleport module is disabled the binding
 * never happens and the COMPASS gadget (and {@code /stafflist}) stays the no-op {@code NONE}, so the gadget
 * degrades to "teleport could not be started" rather than failing.
 */
@NullMarked
public final class MutableStaffTeleport implements StaffTeleport {

    private final AtomicReference<StaffTeleport> delegate = new AtomicReference<>(StaffTeleport.NONE);

    @Override
    public boolean teleportTo(PlayerRef staff, PlayerRef target) {
        return Objects.requireNonNull(delegate.get(), "delegate").teleportTo(staff, target);
    }

    /** Rebind to the real teleport-provided teleport; called once when the teleport context is enabled. */
    public void bind(StaffTeleport real) {
        delegate.set(Objects.requireNonNull(real, "real"));
    }
}
