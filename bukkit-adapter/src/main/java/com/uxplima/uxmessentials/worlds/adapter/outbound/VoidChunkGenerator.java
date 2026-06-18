package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.Objects;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The built-in {@code uxmEssentials:void} chunk generator: produces a completely empty world painted
 * with one fixed biome. Every vanilla generation stage is suppressed and no {@code generate*} method is
 * overridden, so Paper writes nothing into each chunk and the world is pure void.
 *
 * <p>The generator holds only the immutable, injected {@link BiomeProvider}, so it is safe for Paper's
 * parallel, off-tick worldgen threads.
 */
@NullMarked
public final class VoidChunkGenerator extends ChunkGenerator {

    private final BiomeProvider biomeProvider;

    public VoidChunkGenerator(BiomeProvider biomeProvider) {
        this.biomeProvider = Objects.requireNonNull(biomeProvider, "biomeProvider");
    }

    // Suppress every vanilla generation stage. Paper's four-arg shouldGenerate* overloads delegate to
    // these no-arg forms by default, so overriding the no-arg forms covers both. There is no bedrock
    // floor, caves, surface, decorations, mobs or structures in a void world.

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    // shouldGenerateBedrock has no four-arg overload and is deprecated (since 1.19.2, not for removal);
    // its default already returns false, but we override it explicitly so the void contract is complete.
    @Override
    @SuppressWarnings("deprecation")
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return biomeProvider;
    }

    // No fixed spawn: a void world has no terrain to spawn on, so operators choose the spawn point
    // explicitly via /worlds setspawn. Returning null lets Bukkit fall back to the world's stored spawn.
    @Override
    public @Nullable Location getFixedSpawnLocation(World world, Random random) {
        return null;
    }
}
