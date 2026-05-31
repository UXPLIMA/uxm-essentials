package com.uxplima.uxmessentials.persistence.vaults;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Vaults.VAULTS;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.VaultsRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link VaultRepository} over the generated {@code VAULTS} table. A point read resolves one
 * owner's vault by the {@code (owner, idx)} primary key; the listing reads an owner's indices in ascending
 * order off the {@code idx_vaults_owner} index; the count is a {@code COUNT(*)} so the amount-quota check never
 * materialises the rows. A {@code save} upserts on the primary key — a re-save of the same vault overwrites its
 * size, contents and last-touched in place — so a close-save never inserts a duplicate row. Every statement is
 * typed jOOQ DSL; no SQL is ever string-concatenated.
 */
public final class JooqVaultRepository extends JooqRepository implements VaultRepository {

    public JooqVaultRepository(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<Vault> find(VaultId id) {
        Objects.requireNonNull(id, "id");
        return read(dsl -> dsl.selectFrom(VAULTS)
                .where(VAULTS.OWNER.eq(id.owner().toString()))
                .and(VAULTS.IDX.eq(id.index()))
                .fetchOptional()
                .map(VaultRows::toVault));
    }

    @Override
    public List<Integer> ownedIndices(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.select(VAULTS.IDX)
                .from(VAULTS)
                .where(VAULTS.OWNER.eq(owner.uuid().toString()))
                .orderBy(VAULTS.IDX.asc())
                .fetch(VAULTS.IDX));
    }

    @Override
    public int count(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.fetchCount(VAULTS, VAULTS.OWNER.eq(owner.uuid().toString())));
    }

    @Override
    public void save(Vault vault) {
        Objects.requireNonNull(vault, "vault");
        write(dsl -> {
            upsert(dsl, vault);
            return null;
        });
    }

    private static void upsert(DSLContext dsl, Vault vault) {
        VaultsRecord record = dsl.newRecord(VAULTS);
        VaultRows.apply(record, vault);
        dsl.insertInto(VAULTS)
                .set(record)
                .onConflict(VAULTS.OWNER, VAULTS.IDX)
                .doUpdate()
                .set(VAULTS.SIZE, record.getSize())
                .set(VAULTS.LAST_TOUCHED, record.getLastTouched())
                .set(VAULTS.CONTENTS, record.getContents())
                .execute();
    }
}
