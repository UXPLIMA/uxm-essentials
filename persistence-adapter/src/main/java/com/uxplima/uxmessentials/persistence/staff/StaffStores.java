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
 * swappable in tests. The {@code serverId} is this backend's {@code network.server-id}: the loadout is keyed
 * per {@code (player, server_id)} so two backends sharing one DB never clobber each other's captured row.
 */
@NullMarked
public final class StaffStores {

    private StaffStores() {}

    /**
     * A jOOQ {@link StaffLoadoutRepository} over the shared persistence DSL, stamping captures from {@code clock}
     * and scoping every row to {@code serverId} (this backend's {@code network.server-id}).
     */
    public static StaffLoadoutRepository loadouts(Persistence persistence, Clock clock, String serverId) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(serverId, "serverId");
        return new JooqStaffLoadoutRepository(persistence.dsl(), clock, serverId);
    }
}
