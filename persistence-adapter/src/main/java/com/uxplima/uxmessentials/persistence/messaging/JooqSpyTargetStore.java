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
 * watch. A change adds and removes single rows in one transaction, so a row another backend added stays.
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
    public void change(UUID spy, Set<UUID> added, Set<UUID> removed) {
        Objects.requireNonNull(spy, "spy");
        Objects.requireNonNull(added, "added");
        Objects.requireNonNull(removed, "removed");
        String owner = spy.toString();
        write(dsl -> {
            if (!removed.isEmpty()) {
                dsl.deleteFrom(MESSAGING_SPY_TARGETS)
                        .where(MESSAGING_SPY_TARGETS.SPY_UUID.eq(owner))
                        .and(MESSAGING_SPY_TARGETS.TARGET_UUID.in(
                                removed.stream().map(UUID::toString).toList()))
                        .execute();
            }
            for (UUID target : added) {
                dsl.insertInto(MESSAGING_SPY_TARGETS)
                        .set(MESSAGING_SPY_TARGETS.SPY_UUID, owner)
                        .set(MESSAGING_SPY_TARGETS.TARGET_UUID, target.toString())
                        .onConflictDoNothing()
                        .execute();
            }
            return null;
        });
    }
}
