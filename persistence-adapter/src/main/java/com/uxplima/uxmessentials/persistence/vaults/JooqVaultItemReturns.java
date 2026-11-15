package com.uxplima.uxmessentials.persistence.vaults;

import static com.uxplima.uxmessentials.persistence.jooq.tables.VaultItemReturns.VAULT_ITEM_RETURNS;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.vaults.application.port.VaultItemReturns;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * jOOQ-backed {@link VaultItemReturns} over the {@code vault_item_returns} table. The items are stored in the vaults
 * table's own base64 form ({@link VaultRows#encode}).
 *
 * <p>The take-all reads first and opens a write only when there are rows. It then deletes the rows one by one and
 * keeps a row only when its own delete removed it. Two takes running at once (a quick rejoin, or two servers on one
 * database) can both read the same rows, but each row is deleted by exactly one of them, so no item is handed out
 * twice, on any backend's isolation level.
 */
@NullMarked
public final class JooqVaultItemReturns extends JooqRepository implements VaultItemReturns {

    /** One kept row as read, before it is claimed. */
    private record KeptRow(String id, String contents) {}

    private final Clock clock;

    public JooqVaultItemReturns(DSLContext dsl, Clock clock) {
        super(dsl);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void keep(UUID owner, VaultContents items) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(items, "items");
        String encoded = VaultRows.encode(items);
        if (encoded == null) {
            return;
        }
        long now = clock.millis();
        write(dsl -> dsl.insertInto(VAULT_ITEM_RETURNS)
                .set(VAULT_ITEM_RETURNS.ID, UUID.randomUUID().toString())
                .set(VAULT_ITEM_RETURNS.OWNER, owner.toString())
                .set(VAULT_ITEM_RETURNS.CONTENTS, encoded)
                .set(VAULT_ITEM_RETURNS.CREATED_AT, now)
                .execute());
    }

    @Override
    public List<VaultContents> takeAll(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        List<KeptRow> rows = read(dsl -> dsl.select(VAULT_ITEM_RETURNS.ID, VAULT_ITEM_RETURNS.CONTENTS)
                .from(VAULT_ITEM_RETURNS)
                .where(VAULT_ITEM_RETURNS.OWNER.eq(owner.toString()))
                .orderBy(VAULT_ITEM_RETURNS.CREATED_AT, VAULT_ITEM_RETURNS.ID)
                .fetch(row -> new KeptRow(row.value1(), row.value2())));
        if (rows.isEmpty()) {
            return List.of();
        }
        return write(dsl -> {
            List<VaultContents> taken = new ArrayList<>();
            for (KeptRow row : rows) {
                int removed = dsl.deleteFrom(VAULT_ITEM_RETURNS)
                        .where(VAULT_ITEM_RETURNS.ID.eq(row.id()))
                        .execute();
                // A row another take deleted first is that take's to hand over.
                if (removed == 1) {
                    taken.add(VaultRows.decode(row.contents()));
                }
            }
            return taken;
        });
    }
}
