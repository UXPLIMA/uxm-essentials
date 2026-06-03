package com.uxplima.uxmessentials.persistence.economy;

import java.math.BigDecimal;
import java.util.Locale;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.EconomyWorthOverridesRecord;

/**
 * The anti-corruption mapping between an {@code economy_worth_overrides} row and the {@code /setworth} override
 * the use cases set: a canonical lowercase material id and its unit price. The price is {@code DECIMAL(20,4)},
 * the same width every balance is stored at, so a worth round-trips as the exact {@link BigDecimal} a sell
 * computes against. This class is the single place that translation lives.
 *
 * <p>The {@code defined_by} actor uuid and {@code updated_at} timestamp are persistence metadata, not part of
 * the override the port exposes, so {@link #apply} stamps the timestamp at save time and leaves the actor null
 * (the port carries only material and price); a read needs neither.
 */
final class WorthOverrideRows {

    private WorthOverrideRows() {}

    /** The canonical lowercase form of a material id, matching how the config worth table case-folds. */
    static String normalise(String material) {
        return material.toLowerCase(Locale.ROOT);
    }

    /** Populate a record from a material id and price for an upsert, stamping the save timestamp. */
    static void apply(EconomyWorthOverridesRecord record, String material, BigDecimal price, long savedAtEpochMillis) {
        record.setMaterial(normalise(material)).setPrice(price).setUpdatedAt(savedAtEpochMillis);
    }
}
