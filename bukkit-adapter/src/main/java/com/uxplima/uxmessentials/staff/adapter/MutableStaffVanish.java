package com.uxplima.uxmessentials.staff.adapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import org.jspecify.annotations.NullMarked;

/**
 * A rebindable {@link StaffVanish} holder: it forwards to {@link StaffVanish#NONE} until the presence-backed
 * vanish is bound in, mirroring the messaging context's {@code MutableMutePolicy}. When the presence module is
 * disabled the binding never happens and staff vanish stays the no-op {@code NONE}, so the gadget and the
 * vanish-on-enter degrade rather than fail.
 */
@NullMarked
public final class MutableStaffVanish implements StaffVanish {

    private final AtomicReference<StaffVanish> delegate = new AtomicReference<>(StaffVanish.NONE);

    @Override
    public void setVanished(PlayerRef who, boolean vanished) {
        Objects.requireNonNull(delegate.get(), "delegate").setVanished(who, vanished);
    }

    /** Rebind to the real presence-provided vanish; called once when the presence context is enabled. */
    public void bind(StaffVanish real) {
        delegate.set(Objects.requireNonNull(real, "real"));
    }
}
