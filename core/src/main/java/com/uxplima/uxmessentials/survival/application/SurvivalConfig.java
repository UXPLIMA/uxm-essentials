package com.uxplima.uxmessentials.survival.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/survival/config.conf}: the module enable gate plus one sub-record per
 * mechanic. Each mechanic carries its own {@code enabled} switch on top of the module gate, so an operator turns the
 * whole context off with {@code enabled = false} or leaves it on and enables exactly the mechanics they want.
 *
 * <p>It is resolved once from the module's scoped {@link ConfigStore} when the module starts and, per the
 * atomic-reload rule, swapped whole on reload — so a block-break handled mid-reload sees one coherent snapshot. The
 * HOCON keys are kebab-case ({@code tree-feller}, {@code require-axe}, {@code max-blocks}); the record components are
 * the camelCase views the adapter reads. Every knob carries the default the bundled config ships, so an operator who
 * deletes a line falls back to the shipped value rather than to zero.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param treeFeller the tree-feller mechanic settings
 * @param veinminer the veinminer mechanic settings
 */
public record SurvivalConfig(boolean enabled, TreeFeller treeFeller, Veinminer veinminer) {

    public SurvivalConfig {
        Objects.requireNonNull(treeFeller, "treeFeller");
        Objects.requireNonNull(veinminer, "veinminer");
    }

    /** Resolve the survival config from the module's scoped {@link ConfigStore} ({@code modules.survival}). */
    public static SurvivalConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new SurvivalConfig(config.getBoolean("enabled", true), TreeFeller.from(config), Veinminer.from(config));
    }

    /**
     * The tree-feller mechanic under {@code tree-feller { … }}: breaking one log of a tree fells every connected log
     * of the same kind, up to {@code max-blocks}.
     *
     * @param enabled whether tree-feller runs ({@code tree-feller.enabled}, default {@code true})
     * @param requireAxe whether the player must hold an axe to fell ({@code require-axe}, default {@code true})
     * @param durabilityDrain whether felling drains the axe's durability ({@code durability-drain}, default true)
     * @param durabilityMultiplier the durability cost per extra log felled ({@code durability-multiplier}, default 1)
     * @param maxBlocks the most logs a single fell breaks, the origin included ({@code max-blocks}, default 64)
     * @param hungerCost the exhaustion added per extra log felled ({@code hunger-cost}, default 0.0)
     * @param sneakRequired whether the player must be sneaking to fell ({@code sneak-required}, default {@code false})
     * @param replantSaplings whether a matching sapling is replanted at the base ({@code replant-saplings}, default
     *     {@code true})
     */
    public record TreeFeller(
            boolean enabled,
            boolean requireAxe,
            boolean durabilityDrain,
            int durabilityMultiplier,
            int maxBlocks,
            double hungerCost,
            boolean sneakRequired,
            boolean replantSaplings) {

        public TreeFeller {
            maxBlocks = Math.max(1, maxBlocks);
            durabilityMultiplier = Math.max(0, durabilityMultiplier);
            if (!Double.isFinite(hungerCost) || hungerCost < 0) {
                throw new IllegalArgumentException("tree-feller hunger-cost must be finite and non-negative");
            }
        }

        static TreeFeller from(ConfigStore config) {
            return new TreeFeller(
                    config.getBoolean("tree-feller.enabled", true),
                    config.getBoolean("tree-feller.require-axe", true),
                    config.getBoolean("tree-feller.durability-drain", true),
                    config.getInt("tree-feller.durability-multiplier", 1),
                    config.getInt("tree-feller.max-blocks", 64),
                    config.getDouble("tree-feller.hunger-cost", 0.0),
                    config.getBoolean("tree-feller.sneak-required", false),
                    config.getBoolean("tree-feller.replant-saplings", true));
        }
    }

    /**
     * The veinminer mechanic under {@code veinminer { … }}: breaking one block from {@code blocks} breaks every
     * connected block of the same material, up to {@code max-blocks}.
     *
     * @param enabled whether veinminer runs ({@code veinminer.enabled}, default {@code true})
     * @param blocks the material ids that trigger a vein-break ({@code blocks}, default the common ore set)
     * @param maxBlocks the most blocks a single vein breaks, the origin included ({@code max-blocks}, default 64)
     * @param toolDurability whether veining drains the tool's durability ({@code tool-durability}, default true)
     * @param hungerCost the exhaustion added per extra block broken ({@code hunger-cost}, default 0.0)
     * @param sneakRequired whether the player must be sneaking to vein ({@code sneak-required}, default {@code true})
     */
    public record Veinminer(
            boolean enabled,
            List<String> blocks,
            int maxBlocks,
            boolean toolDurability,
            double hungerCost,
            boolean sneakRequired) {

        /** The default trigger set — every ore, its deepslate variant, and the nether ores plus ancient debris. */
        private static final List<String> DEFAULT_BLOCKS = List.of(
                "COAL_ORE",
                "DEEPSLATE_COAL_ORE",
                "IRON_ORE",
                "DEEPSLATE_IRON_ORE",
                "COPPER_ORE",
                "DEEPSLATE_COPPER_ORE",
                "GOLD_ORE",
                "DEEPSLATE_GOLD_ORE",
                "REDSTONE_ORE",
                "DEEPSLATE_REDSTONE_ORE",
                "LAPIS_ORE",
                "DEEPSLATE_LAPIS_ORE",
                "DIAMOND_ORE",
                "DEEPSLATE_DIAMOND_ORE",
                "EMERALD_ORE",
                "DEEPSLATE_EMERALD_ORE",
                "NETHER_GOLD_ORE",
                "NETHER_QUARTZ_ORE",
                "ANCIENT_DEBRIS");

        public Veinminer {
            Objects.requireNonNull(blocks, "blocks");
            blocks = List.copyOf(blocks);
            maxBlocks = Math.max(1, maxBlocks);
            if (!Double.isFinite(hungerCost) || hungerCost < 0) {
                throw new IllegalArgumentException("veinminer hunger-cost must be finite and non-negative");
            }
        }

        static Veinminer from(ConfigStore config) {
            return new Veinminer(
                    config.getBoolean("veinminer.enabled", true),
                    config.getStringList("veinminer.blocks", DEFAULT_BLOCKS),
                    config.getInt("veinminer.max-blocks", 64),
                    config.getBoolean("veinminer.tool-durability", true),
                    config.getDouble("veinminer.hunger-cost", 0.0),
                    config.getBoolean("veinminer.sneak-required", true));
        }
    }
}
