package com.uxplima.uxmessentials.vaults.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.jspecify.annotations.NullMarked;

/**
 * Typed view over the {@code vaults.conf} subtree: the per-context fallbacks the two numbered-quota families
 * fold against when a player holds no matching node — {@code default-amount} (how many vaults) and
 * {@code default-size} (rows per vault) — plus the optional {@code economy} block (whether vault actions cost
 * and how much). Read once at wire time from the module's scoped config. The size default is clamped into the
 * renderable {@code [1, 6]} row range so a misconfigured value never produces an inventory a chest GUI cannot
 * show.
 */
@NullMarked
public final class VaultSettings {

    private static final int DEFAULT_AMOUNT = 1;
    private static final int DEFAULT_SIZE = 6;

    private final int defaultAmount;
    private final int defaultSize;
    private final boolean economyEnabled;
    private final VaultChargeSettings chargeSettings;

    public VaultSettings(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        this.defaultAmount = Math.max(0, config.getInt("default-amount", DEFAULT_AMOUNT));
        this.defaultSize = clampSize(config.getInt("default-size", DEFAULT_SIZE));
        this.economyEnabled = config.getBoolean("economy.enabled", false);
        this.chargeSettings = VaultChargeSettings.of(
                config.getDouble("economy.cost-to-create", 0),
                config.getDouble("economy.cost-to-open", 0),
                config.getDouble("economy.refund-on-delete", 0));
    }

    /** The config fallback for the {@code uxmessentials.vault.amount.<n>} quota. */
    public int defaultAmount() {
        return defaultAmount;
    }

    /** The config fallback for the {@code uxmessentials.vault.size.<rows>} quota, in renderable rows. */
    public int defaultSize() {
        return defaultSize;
    }

    /** Whether the {@code economy} block opts vault actions into a per-action cost (requires a wired provider). */
    public boolean economyEnabled() {
        return economyEnabled;
    }

    /** The create/open fees and the delete refund, parsed once from the {@code economy} block. */
    public VaultChargeSettings chargeSettings() {
        return chargeSettings;
    }

    private static int clampSize(int rows) {
        return Math.max(VaultSize.MIN_ROWS, Math.min(VaultSize.MAX_ROWS, rows));
    }
}
