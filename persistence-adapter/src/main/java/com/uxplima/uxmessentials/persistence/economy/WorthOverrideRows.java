package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyWorthOverrides.ECONOMY_WORTH_OVERRIDES;

import java.math.BigDecimal;
import java.util.Locale;

import com.uxplima.uxmessentials.economy.application.Worth;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.EconomyWorthOverridesRecord;
import org.jooq.Record;

/**
 * The anti-corruption mapping between an {@code economy_worth_overrides} row and the {@code /setworth} override
 * the use cases set: a canonical lowercase material id, its unit price, and the currency it pays out in. The
 * price is {@code DECIMAL(20,4)}, the same width every balance is stored at, so a worth round-trips as the exact
 * {@link BigDecimal} a sell computes against. This class is the single place that translation lives.
 *
 * <p>The {@code currency} column (V63) records the pay-out currency, so an override set in-game can name any
 * configured currency just like the config worth table; a row predating V63 reads back as {@code 'default'}.
 * The {@code defined_by} actor uuid and {@code updated_at} timestamp are persistence metadata, not part of the
 * override the port exposes, so {@link #apply} stamps the timestamp at save time and leaves the actor null (the
 * port carries only material, price and currency); a read needs neither.
 */
final class WorthOverrideRows {

    private WorthOverrideRows() {}

    /** The canonical lowercase form of a material id, matching how the config worth table case-folds. */
    static String normalise(String material) {
        return material.toLowerCase(Locale.ROOT);
    }

    /** Rebuild a {@link Worth} (price and pay-out currency) from a queried override row. */
    static Worth toWorth(Record row) {
        return Worth.of(row.get(ECONOMY_WORTH_OVERRIDES.PRICE), row.get(ECONOMY_WORTH_OVERRIDES.CURRENCY));
    }

    /** Populate a record from a material id, price and currency for an upsert, stamping the save timestamp. */
    static void apply(
            EconomyWorthOverridesRecord record,
            String material,
            BigDecimal price,
            String currencyId,
            long savedAtEpochMillis) {
        record.setMaterial(normalise(material))
                .setPrice(price)
                .setCurrency(currencyId)
                .setUpdatedAt(savedAtEpochMillis);
    }
}
