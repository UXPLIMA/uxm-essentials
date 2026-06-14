package com.uxplima.uxmessentials.persistence.staff;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the staff context's persistence adapter, so the consuming bukkit-adapter wires the
 * {@link StaffLoadoutRepository} from the {@link Persistence} handle it already holds without ever naming a
 * jOOQ type (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile
 * classpath).
 *
 * <p>The loadout repository is the plain jOOQ adapter (no cache): a row is written once on enter and read
 * once on exit, so caching would only add invalidation surface for no read benefit. The injected {@link Clock}
 * is the capture-instant source the {@code entered_at} column records, kept here so the time source stays
 * swappable in tests.
 */
@NullMarked
public final class StaffStores {

    private StaffStores() {}

    /** A jOOQ {@link StaffLoadoutRepository} over the shared persistence DSL, stamping captures from {@code clock}. */
    public static StaffLoadoutRepository loadouts(Persistence persistence, Clock clock) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(clock, "clock");
        return new JooqStaffLoadoutRepository(persistence.dsl(), clock);
    }
}
