package com.uxplima.uxmessentials.regions.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.regions.domain.FlagDescriptor;
import com.uxplima.uxmessentials.regions.domain.FlagKind;
import org.jspecify.annotations.NullMarked;

/**
 * One row of the flag editor: a registered flag's {@link FlagDescriptor}. The descriptor is read from the
 * {@link com.uxplima.uxmessentials.regions.application.port.RegionService} once, off the tick thread, before the panel
 * opens, so the icon renderer paints from this snapshot and never re-queries WorldGuard on the entity thread; a click
 * reads the {@link #kind()} to know which control to open and the {@link #value()} to know what it currently holds.
 *
 * @param descriptor the flag's name, portable kind, current value, and (for a choice flag) its choices
 */
@NullMarked
public record FlagRow(FlagDescriptor descriptor) {

    public FlagRow {
        Objects.requireNonNull(descriptor, "descriptor");
    }

    /** The flag's registered name. */
    public String flag() {
        return descriptor.name();
    }

    /** The flag's portable kind, which decides the control a click opens. */
    public FlagKind kind() {
        return descriptor.kind();
    }

    /** The flag's current portable value in the region, or empty when it is unset. */
    public String value() {
        return descriptor.value();
    }
}
