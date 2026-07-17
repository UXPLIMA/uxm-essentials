package com.uxplima.uxmessentials.regions.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.regions.domain.FlagState;
import org.jspecify.annotations.NullMarked;

/**
 * One row of the flag editor: a configured flag name and its current {@link FlagState}. The state is read from the
 * {@link com.uxplima.uxmessentials.regions.application.port.RegionService} once, off the tick thread, before the
 * panel opens, so the icon renderer paints from this snapshot and never re-queries WorldGuard on the entity thread;
 * a click reads {@link #state} to know which value to cycle to.
 *
 * @param flag the flag's registered name (a WorldGuard state flag, e.g. {@code pvp})
 * @param state the flag's current value in the region
 */
@NullMarked
public record FlagRow(String flag, FlagState state) {

    public FlagRow {
        Objects.requireNonNull(flag, "flag");
        Objects.requireNonNull(state, "state");
        if (flag.isBlank()) {
            throw new IllegalArgumentException("flag name must not be blank");
        }
    }
}
