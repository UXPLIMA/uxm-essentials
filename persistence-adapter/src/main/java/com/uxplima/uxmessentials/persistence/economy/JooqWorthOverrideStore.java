package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyWorthOverrides.ECONOMY_WORTH_OVERRIDES;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.WorthOverrideStore;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.EconomyWorthOverridesRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link WorthOverrideStore} over the generated {@code ECONOMY_WORTH_OVERRIDES} table. An
 * override is keyed by its canonical lowercase material id, so a lookup is a single-row {@code SELECT} on the
 * {@code material} primary key, {@link #set} upserts on that key — a {@code /setworth} onto an existing material
 * overwrites the same row — and {@link #remove} deletes by it. Every statement is typed jOOQ DSL; no SQL is
 * ever string-concatenated.
 *
 * <p>The {@code updated_at} metadata column is stamped at save time from the wall clock; the port carries only
 * the material and price, so the column is a write-only audit field the read path does not return.
 */
public final class JooqWorthOverrideStore extends JooqRepository implements WorthOverrideStore {

    public JooqWorthOverrideStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public void set(String material, BigDecimal price) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(price, "price");
        long savedAt = System.currentTimeMillis();
        write(dsl -> {
            upsert(dsl, material, price, savedAt);
            return null;
        });
    }

    @Override
    public Optional<BigDecimal> find(String material) {
        Objects.requireNonNull(material, "material");
        String key = WorthOverrideRows.normalise(material);
        return read(dsl -> dsl.select(ECONOMY_WORTH_OVERRIDES.PRICE)
                .from(ECONOMY_WORTH_OVERRIDES)
                .where(ECONOMY_WORTH_OVERRIDES.MATERIAL.eq(key))
                .fetchOptional(ECONOMY_WORTH_OVERRIDES.PRICE));
    }

    @Override
    public boolean exists(String material) {
        Objects.requireNonNull(material, "material");
        String key = WorthOverrideRows.normalise(material);
        return read(dsl -> dsl.fetchExists(ECONOMY_WORTH_OVERRIDES, ECONOMY_WORTH_OVERRIDES.MATERIAL.eq(key)));
    }

    @Override
    public boolean remove(String material) {
        Objects.requireNonNull(material, "material");
        String key = WorthOverrideRows.normalise(material);
        return write(dsl -> dsl.deleteFrom(ECONOMY_WORTH_OVERRIDES)
                        .where(ECONOMY_WORTH_OVERRIDES.MATERIAL.eq(key))
                        .execute()
                > 0);
    }

    private static void upsert(DSLContext dsl, String material, BigDecimal price, long savedAt) {
        EconomyWorthOverridesRecord record = dsl.newRecord(ECONOMY_WORTH_OVERRIDES);
        WorthOverrideRows.apply(record, material, price, savedAt);
        dsl.insertInto(ECONOMY_WORTH_OVERRIDES)
                .set(record)
                .onConflict(ECONOMY_WORTH_OVERRIDES.MATERIAL)
                .doUpdate()
                .set(ECONOMY_WORTH_OVERRIDES.PRICE, record.getPrice())
                .set(ECONOMY_WORTH_OVERRIDES.UPDATED_AT, record.getUpdatedAt())
                .execute();
    }
}
