package com.uxplima.uxmessentials.persistence.messaging;

import static com.uxplima.uxmessentials.persistence.jooq.tables.MessagingSpyTargets.MESSAGING_SPY_TARGETS;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.messaging.application.port.SpyTargetStore;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link SpyTargetStore} over {@code messaging_spy_targets}: one row per staff member and player they
 * watch. A save replaces the staff member's whole set in one transaction, so a reader never sees half of it.
 */
public final class JooqSpyTargetStore extends JooqRepository implements SpyTargetStore {

    public JooqSpyTargetStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Set<UUID> load(UUID spy) {
        Objects.requireNonNull(spy, "spy");
        return read(dsl -> dsl.select(MESSAGING_SPY_TARGETS.TARGET_UUID)
                        .from(MESSAGING_SPY_TARGETS)
                        .where(MESSAGING_SPY_TARGETS.SPY_UUID.eq(spy.toString()))
                        .fetch(MESSAGING_SPY_TARGETS.TARGET_UUID))
                .stream()
                .map(UUID::fromString)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void save(UUID spy, Set<UUID> targets) {
        Objects.requireNonNull(spy, "spy");
        Objects.requireNonNull(targets, "targets");
        write(dsl -> {
            dsl.deleteFrom(MESSAGING_SPY_TARGETS)
                    .where(MESSAGING_SPY_TARGETS.SPY_UUID.eq(spy.toString()))
                    .execute();
            for (UUID target : targets) {
                dsl.insertInto(MESSAGING_SPY_TARGETS)
                        .set(MESSAGING_SPY_TARGETS.SPY_UUID, spy.toString())
                        .set(MESSAGING_SPY_TARGETS.TARGET_UUID, target.toString())
                        .execute();
            }
            return null;
        });
    }
}
