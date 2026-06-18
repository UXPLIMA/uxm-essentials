package com.uxplima.uxmessentials.worlds.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The ordered (bottom→top) stack of block bands for a flat world, resolved once from config into an
 * immutable snapshot. {@link #total()} is the combined height; the adapter pre-composes each band's
 * block data and reuses it per chunk so worldgen never allocates per block.
 */
public record FlatLayerPlan(List<FlatLayer> layers) {

    public FlatLayerPlan {
        Objects.requireNonNull(layers, "layers");
        layers = List.copyOf(layers);
    }

    /** The summed height of every band. */
    public int total() {
        return layers.stream().mapToInt(FlatLayer::height).sum();
    }

    /** The vanilla "Classic Flat" shape: bedrock×1, dirt×3, grass_block×1 (bottom→top). */
    public static FlatLayerPlan defaults() {
        return new FlatLayerPlan(List.of(
                new FlatLayer(BlockId.of("minecraft:bedrock"), 1),
                new FlatLayer(BlockId.of("minecraft:dirt"), 3),
                new FlatLayer(BlockId.of("minecraft:grass_block"), 1)));
    }

    /**
     * Parses config layer strings of the form {@code "<blockId> <height>"} (bottom→top). Malformed
     * entries (wrong arity, bad block id, non-numeric or sub-1 height) are skipped; an empty or
     * fully-malformed input falls back to {@link #defaults()}.
     */
    public static FlatLayerPlan parse(List<String> raw) {
        Objects.requireNonNull(raw, "raw");
        List<FlatLayer> parsed = raw.stream()
                .map(FlatLayerPlan::parseLayer)
                .flatMap(Optional::stream)
                .toList();
        return parsed.isEmpty() ? defaults() : new FlatLayerPlan(parsed);
    }

    private static Optional<FlatLayer> parseLayer(String entry) {
        if (entry == null) {
            return Optional.empty();
        }
        String[] parts = entry.trim().split(" ", -1);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            int height = Integer.parseInt(parts[1].trim());
            if (height < 1) {
                return Optional.empty();
            }
            return Optional.of(new FlatLayer(BlockId.of(parts[0]), height));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
