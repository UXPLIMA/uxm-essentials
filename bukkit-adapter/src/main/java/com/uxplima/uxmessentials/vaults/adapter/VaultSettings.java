package com.uxplima.uxmessentials.vaults.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultSelectorView.VaultSelectorSettings;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.domain.VaultItemPolicy;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.jspecify.annotations.NullMarked;

/**
 * Typed view over the {@code vaults.conf} subtree: the per-context fallbacks the two numbered-quota families
 * fold against when a player holds no matching node — {@code default-amount} (how many vaults) and
 * {@code default-size} (rows per vault) — plus the optional {@code economy} block (whether vault actions cost
 * and how much), the {@code blacklist-materials} list (materials a vault refuses to store), and the
 * {@code selector} block (the {@code /vault} picker menu). Read once at wire time from the module's scoped
 * config. The size default is clamped into the renderable {@code [1, 6]} row range so a misconfigured value
 * never produces an inventory a chest GUI cannot show, and an unknown selector icon material falls back to a
 * sensible default rather than failing the wire.
 */
@NullMarked
public final class VaultSettings {

    private static final int DEFAULT_AMOUNT = 1;
    private static final int DEFAULT_SIZE = 6;
    private static final int DEFAULT_SELECTOR_ROWS = 3;
    private static final Material DEFAULT_OWNED_ICON = Material.CHEST;
    private static final Material DEFAULT_LOCKED_ICON = Material.GRAY_STAINED_GLASS_PANE;

    private final int defaultAmount;
    private final int defaultSize;
    private final boolean economyEnabled;
    private final VaultChargeSettings chargeSettings;
    private final VaultItemPolicy itemPolicy;
    private final boolean selectorEnabled;
    private final VaultSelectorSettings selectorSettings;

    public VaultSettings(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        this.defaultAmount = Math.max(0, config.getInt("default-amount", DEFAULT_AMOUNT));
        this.defaultSize = clampSize(config.getInt("default-size", DEFAULT_SIZE));
        this.economyEnabled = config.getBoolean("economy.enabled", false);
        this.chargeSettings = VaultChargeSettings.of(
                config.getDouble("economy.cost-to-create", 0),
                config.getDouble("economy.cost-to-open", 0),
                config.getDouble("economy.refund-on-delete", 0));
        this.itemPolicy = new VaultItemPolicy(Set.copyOf(config.getStringList("blacklist-materials", List.of())));
        this.selectorEnabled = config.getBoolean("selector.enabled", true);
        this.selectorSettings = parseSelector(config);
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

    /** The item-blacklist policy built from the {@code blacklist-materials} list (normalised, immutable). */
    public VaultItemPolicy itemPolicy() {
        return itemPolicy;
    }

    /** Whether {@code /vault} (with several vaults owned) opens the picker menu instead of the chat list. */
    public boolean selectorEnabled() {
        return selectorEnabled;
    }

    /** The picker-menu presentation (layout, icons, show-locked) parsed once from the {@code selector} block. */
    public VaultSelectorSettings selectorSettings() {
        return selectorSettings;
    }

    private VaultSelectorSettings parseSelector(ConfigStore config) {
        int rows = clampRows(config.getInt("selector.rows", DEFAULT_SELECTOR_ROWS));
        boolean showLocked = config.getBoolean("selector.show-locked", true);
        Material owned = icon(config.getString("selector.owned-icon", ""), DEFAULT_OWNED_ICON);
        Material locked = icon(config.getString("selector.locked-icon", ""), DEFAULT_LOCKED_ICON);
        // Pin the nav buttons into the configured menu's bottom row, leaving the rows above for the vault icons.
        int bottomRow = (rows - 1) * 9;
        GuiLayout layout = new GuiLayout(rows, owned, Material.ARROW, bottomRow + 3, bottomRow + 5, List.of());
        return new VaultSelectorSettings(layout, owned, locked, showLocked);
    }

    /** Resolve a configured material name, falling back to {@code fallback} for a blank or unknown value. */
    private static Material icon(String name, Material fallback) {
        if (name.isBlank()) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(name);
        return parsed != null && !parsed.isAir() ? parsed : fallback;
    }

    private static int clampSize(int rows) {
        return Math.max(VaultSize.MIN_ROWS, Math.min(VaultSize.MAX_ROWS, rows));
    }

    private static int clampRows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }
}
