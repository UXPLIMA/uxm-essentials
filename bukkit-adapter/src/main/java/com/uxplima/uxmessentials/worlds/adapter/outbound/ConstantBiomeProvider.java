package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.List;
import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.worlds.domain.BiomeId;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link BiomeProvider} that paints a whole world with one fixed biome. Used by the built-in void
 * and flat generators. The provider holds only the immutable biome and a cached singleton list, so it
 * is safe for Paper's parallel, off-tick worldgen threads.
 */
@NullMarked
public final class ConstantBiomeProvider extends BiomeProvider {

    private final Biome biome;
    private final List<Biome> biomes;

    public ConstantBiomeProvider(Biome biome) {
        this.biome = Objects.requireNonNull(biome, "biome");
        this.biomes = List.of(biome);
    }

    /**
     * Resolves {@code id} to a Bukkit {@link Biome}, falling back to {@link Biome#PLAINS} (warned once,
     * here at construction) when the id names no registered biome.
     */
    public static ConstantBiomeProvider from(BiomeId id, Logger log) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(log, "log");
        return new ConstantBiomeProvider(resolve(id, log));
    }

    // Registry.BIOME is deprecated (since 1.21.3, not for removal) in favour of RegistryAccess, but it
    // remains the public, MockBukkit-backed way to resolve a Biome by NamespacedKey on the 1.21 line.
    @SuppressWarnings("deprecation")
    private static Biome resolve(BiomeId id, Logger log) {
        NamespacedKey key = NamespacedKey.fromString(id.namespacedValue());
        Biome resolved = key == null ? null : Registry.BIOME.get(key);
        if (resolved != null) {
            return resolved;
        }
        log.warn("unknown biome {}, using plains", id.value());
        return Biome.PLAINS;
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return biome;
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return biomes;
    }
}
