package com.uxplima.uxmessentials.persistence.vaults;

import static com.uxplima.uxmessentials.persistence.jooq.tables.VaultItemReturns.VAULT_ITEM_RETURNS;

import java.time.Clock;
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
 * <p>A batch is claimed by deleting its row, and only the delete that removed it reports the claim. Two deliveries
 * running at once (a quick rejoin, or two servers on one database) can both read the same rows, but each row is
 * deleted by exactly one of them, so no item is handed out twice, on any backend's isolation level.
 */
@NullMarked
public final class JooqVaultItemReturns extends JooqRepository implements VaultItemReturns {

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
    public List<Kept> kept(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.select(VAULT_ITEM_RETURNS.ID, VAULT_ITEM_RETURNS.CONTENTS)
                .from(VAULT_ITEM_RETURNS)
                .where(VAULT_ITEM_RETURNS.OWNER.eq(owner.toString()))
                .orderBy(VAULT_ITEM_RETURNS.CREATED_AT, VAULT_ITEM_RETURNS.ID)
                .fetch(row -> new Kept(row.value1(), VaultRows.decode(row.value2()))));
    }

    @Override
    public boolean claim(String id) {
        Objects.requireNonNull(id, "id");
        return write(dsl -> dsl.deleteFrom(VAULT_ITEM_RETURNS)
                        .where(VAULT_ITEM_RETURNS.ID.eq(id))
                        .execute()
                == 1);
    }
}
