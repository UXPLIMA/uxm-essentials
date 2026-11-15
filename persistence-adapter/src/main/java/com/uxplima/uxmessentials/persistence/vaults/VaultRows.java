package com.uxplima.uxmessentials.persistence.vaults;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.jooq.tables.Vaults;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.VaultsRecord;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultPayment;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.jooq.Record;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption mapping between a {@code vaults} row and the domain {@link Vault}. The queryable facts
 * owner uuid (canonical 36-char text), the one-based index, the size in rows, and the last-touched epoch
 * millis. Are first-class columns; only the {@code ItemStack[]} contents are opaque payload, stored as the
 * base64 text of the adapter's already-serialized item bytes (the architecture persistence invariant). This
 * class is the single place that translation lives, so the row shape stays identical on every backend.
 *
 * <p>A null {@code contents} cell is a freshly allocated, empty vault; it round-trips to
 * {@link VaultContents#empty()} and back to a null cell, so no blob is written for a vault that holds nothing.
 *
 * <p>The payment columns are null together for a vault that cost nothing or whose payment was never recorded;
 * both read back as no payment, which refunds nothing.
 */
final class VaultRows {

    /** The scale of the {@code paid_amount} column, matching the economy's DECIMAL(20, 4) amounts. */
    private static final int PAID_SCALE = 4;

    private VaultRows() {}

    /** Rebuild a {@link Vault} from a queried row. */
    static Vault toVault(Record row) {
        Vaults vaults = Vaults.VAULTS;
        VaultId id = new VaultId(UUID.fromString(row.get(vaults.OWNER)), row.get(vaults.IDX));
        VaultSize size = new VaultSize(row.get(vaults.SIZE));
        VaultContents contents = decode(row.get(vaults.CONTENTS));
        Instant lastTouched = Instant.ofEpochMilli(row.get(vaults.LAST_TOUCHED));
        return Vault.of(
                id,
                size,
                contents,
                row.get(vaults.DISPLAY_NAME),
                row.get(vaults.ICON),
                lastTouched,
                payment(row.get(vaults.PAID_AMOUNT), row.get(vaults.PAID_CURRENCY)));
    }

    /** Populate a {@link VaultsRecord} from a domain {@link Vault} for an upsert. */
    static void apply(VaultsRecord record, Vault vault) {
        record.setOwner(vault.owner().toString())
                .setIdx(vault.index())
                .setSize((short) vault.size().rows())
                .setLastTouched(vault.lastTouched().toEpochMilli())
                .setContents(encode(vault.contents()))
                .setDisplayName(vault.displayName())
                .setIcon(vault.iconMaterial());
    }

    /**
     * Populate the payment columns of {@code record} from {@code vault}, for the insert of a newly created vault.
     * The amount is rounded down to the column's scale: a database that rounds half up would otherwise store a
     * fraction more than was taken, and the stored amount is the refund ceiling.
     */
    static void applyPayment(VaultsRecord record, Vault vault) {
        BigDecimal amount = vault.paid().map(VaultRows::storedAmount).orElse(null);
        record.setPaidAmount(amount)
                .setPaidCurrency(
                        amount == null ? null : vault.paid().orElseThrow().currencyId());
    }

    /**
     * The amount the {@code paid_amount} column holds for {@code paid}, or {@code null} when it rounds to nothing,
     * the same value a conditional delete compares against.
     */
    static @Nullable BigDecimal storedAmount(VaultPayment paid) {
        BigDecimal stored = paid.amount().setScale(PAID_SCALE, RoundingMode.DOWN);
        return stored.signum() > 0 ? stored : null;
    }

    /** The payment a row records, or {@code null} when it records none (a free vault, or payment not recorded). */
    static @Nullable VaultPayment payment(@Nullable BigDecimal amount, @Nullable String currency) {
        if (amount == null || amount.signum() <= 0 || currency == null || currency.isBlank()) {
            return null;
        }
        return new VaultPayment(amount, currency);
    }

    /** Base64 the opaque payload, or {@code null} for an empty vault so no blob is written. */
    static @Nullable String encode(VaultContents contents) {
        return contents.payload().map(Base64.getEncoder()::encodeToString).orElse(null);
    }

    /** Decode a stored base64 cell back into opaque contents; a null or blank cell is an empty vault. */
    static VaultContents decode(@Nullable String cell) {
        if (cell == null || cell.isBlank()) {
            return VaultContents.empty();
        }
        return VaultContents.of(Base64.getDecoder().decode(cell));
    }
}
