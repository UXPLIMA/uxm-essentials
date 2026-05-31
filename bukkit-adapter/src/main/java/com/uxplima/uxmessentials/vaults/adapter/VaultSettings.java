package com.uxplima.uxmessentials.vaults.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.jspecify.annotations.NullMarked;

/**
 * Typed view over the {@code vaults.conf} subtree: the per-context fallbacks the two numbered-quota families
 * fold against when a player holds no matching node — {@code default-amount} (how many vaults) and
 * {@code default-size} (rows per vault). Read once at wire time from the module's scoped config. The size
 * default is clamped into the renderable {@code [1, 6]} row range so a misconfigured value never produces an
 * inventory a chest GUI cannot show.
 */
@NullMarked
public final class VaultSettings {

    private static final int DEFAULT_AMOUNT = 1;
    private static final int DEFAULT_SIZE = 6;

    private final int defaultAmount;
    private final int defaultSize;

    public VaultSettings(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        this.defaultAmount = Math.max(0, config.getInt("default-amount", DEFAULT_AMOUNT));
        this.defaultSize = clampSize(config.getInt("default-size", DEFAULT_SIZE));
    }

    /** The config fallback for the {@code uxmessentials.vault.amount.<n>} quota. */
    public int defaultAmount() {
        return defaultAmount;
    }

    /** The config fallback for the {@code uxmessentials.vault.size.<rows>} quota, in renderable rows. */
    public int defaultSize() {
        return defaultSize;
    }

    private static int clampSize(int rows) {
        return Math.max(VaultSize.MIN_ROWS, Math.min(VaultSize.MAX_ROWS, rows));
    }
}
