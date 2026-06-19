package com.uxplima.uxmessentials.vaults.application;

import org.jspecify.annotations.Nullable;

/**
 * A lightweight read model of one owned vault for the {@code /vault} listing and the selector GUI: its
 * one-based index plus the player's optional display name and icon material name. It is the projection a single
 * {@code summaries} repository read returns for every vault an owner has, so the listing renders each entry's
 * label and icon without loading the full {@code Vault} aggregate (and its contents blob) one by one. The icon
 * is a material <em>name</em>; the adapter resolves it to a Bukkit {@code Material} at the boundary.
 *
 * @param index the one-based vault number the player addresses
 * @param displayName the player-chosen display name, or {@code null} when none has been set
 * @param iconMaterial the player-chosen icon material name, or {@code null} when none has been set
 */
public record VaultSummary(
        int index, @Nullable String displayName, @Nullable String iconMaterial) {

    public VaultSummary {
        if (index < 1) {
            throw new IllegalArgumentException("vault index must be at least 1: " + index);
        }
    }
}
