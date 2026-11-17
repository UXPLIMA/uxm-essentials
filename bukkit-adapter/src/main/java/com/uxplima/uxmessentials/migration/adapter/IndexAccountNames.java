package com.uxplima.uxmessentials.migration.adapter;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.migration.AccountNames;
import com.uxplima.uxmessentials.migration.convert.map.ImportedAccount;
import com.uxplima.uxmessentials.persistence.lookup.JooqPlayerNameRepository;
import org.jspecify.annotations.NullMarked;

/**
 * {@link AccountNames} over the shared {@code player_names} table the name index is backed by. A row is only ever
 * added, never changed, so a name an account joined under here wins over the one a source remembers. A lookup that
 * misses the index's memory reads the table, so an imported account is found by name without a restart.
 */
@NullMarked
final class IndexAccountNames implements AccountNames {

    /**
     * How long a dry run reuses the accounts the table holds. A dry run asks once per account, and reading the table
     * once per account would read it as many times as the source has players.
     */
    private static final Duration HELD_FOR = Duration.ofSeconds(30);

    private final JooqPlayerNameRepository names;
    private final LongSupplier nanos;
    private volatile Held held = new Held(Set.of(), Long.MIN_VALUE);

    IndexAccountNames(JooqPlayerNameRepository names, LongSupplier nanos) {
        this.names = Objects.requireNonNull(names, "names");
        this.nanos = Objects.requireNonNull(nanos, "nanos");
    }

    @Override
    public boolean record(ImportedAccount account) {
        Objects.requireNonNull(account, "account");
        return names.addMissing(List.of(account.row())) > 0;
    }

    @Override
    public boolean holds(ImportedAccount account) {
        Objects.requireNonNull(account, "account");
        long now = nanos.getAsLong();
        Held current = held;
        if (current.readAt() == Long.MIN_VALUE || now - current.readAt() > HELD_FOR.toNanos()) {
            current = new Held(names.knownUuids(), now);
            held = current;
        }
        return current.accounts().contains(account.uuid());
    }

    private record Held(Set<UUID> accounts, long readAt) {}
}
