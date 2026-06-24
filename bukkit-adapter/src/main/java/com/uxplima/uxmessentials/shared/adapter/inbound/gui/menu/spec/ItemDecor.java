package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The cosmetic extras layered onto a rendered item beyond its material, name, and lore — stack amount, an
 * optional custom model data id, an enchant glow, and the raw item-flag tokens the renderer maps to Bukkit
 * {@code ItemFlag}s. Flag tokens stay as strings here so the pure model never references Bukkit.
 */
public record ItemDecor(int amount, Optional<Integer> modelData, boolean glow, List<String> flagTokens) {

    public ItemDecor {
        Objects.requireNonNull(modelData, "modelData");
        flagTokens = List.copyOf(Objects.requireNonNull(flagTokens, "flagTokens"));
    }
}
