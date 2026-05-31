package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import java.util.Locale;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import com.uxplima.uxmessentials.itemworld.domain.EnchantSpec;
import com.uxplima.uxmessentials.itemworld.domain.ItemQuery;
import org.jspecify.annotations.NullMarked;

/**
 * The anti-corruption boundary that resolves the itemworld domain's already-validated, normalised ids against
 * the live Paper registries: an {@link ItemQuery} to a {@link Material}, an {@link EnchantSpec} id to an
 * {@link Enchantment}, and an item-flag token to an {@link ItemFlag}. The domain owns the id <em>shape</em>
 * (lowercase {@code namespace:path}, grammar-checked); this resolver owns the final registry lookup, which is
 * the one remaining failure mode a well-formed-but-unknown id hits — mapped by the caller to the matching
 * {@code ItemWorldError}/{@code MessageKey}.
 *
 * <p>Resolution is pure and side-effect-free: each method returns an {@link Optional} that is empty for an
 * unknown id rather than throwing, so a command renders the localized failure and stops. Only item-typed
 * materials pass {@link #material} — a non-item material (a block-only id) is rejected like an unknown item, so
 * {@code /give} and {@code /item} never hand the player an unobtainable stack.
 */
@NullMarked
public final class BukkitItemResolver {

    private BukkitItemResolver() {}

    /** The {@link Material} for {@code query}, present only when it resolves to an obtainable item. */
    public static Optional<Material> material(ItemQuery query) {
        NamespacedKey key = NamespacedKey.fromString(query.asKey());
        if (key == null) {
            return Optional.empty();
        }
        Material material = Registry.MATERIAL.get(key);
        if (material == null || !material.isItem()) {
            return Optional.empty();
        }
        return Optional.of(material);
    }

    /** The {@link Enchantment} for {@code spec}'s id, present only when it resolves in the registry. */
    public static Optional<Enchantment> enchantment(EnchantSpec spec) {
        NamespacedKey key = NamespacedKey.fromString(spec.enchantId());
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(key));
    }

    /** The {@link ItemFlag} for a case-insensitive token ({@code hide_enchants}), present only when it matches. */
    public static Optional<ItemFlag> itemFlag(String token) {
        String normalised = token.trim().toUpperCase(Locale.ROOT);
        try {
            return Optional.of(ItemFlag.valueOf(normalised));
        } catch (IllegalArgumentException unknownFlag) {
            return Optional.empty();
        }
    }
}
