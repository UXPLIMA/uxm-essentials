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
 * @param leafDecay the fast-leaf-decay mechanic settings
 * @param farmProtect the farmland-protection mechanic settings
 * @param farmAssist the farm-assist mechanic settings
 */
public record SurvivalConfig(
        boolean enabled,
        TreeFeller treeFeller,
        Veinminer veinminer,
        LeafDecay leafDecay,
        FarmProtect farmProtect,
        FarmAssist farmAssist) {

    public SurvivalConfig {
        Objects.requireNonNull(treeFeller, "treeFeller");
        Objects.requireNonNull(veinminer, "veinminer");
        Objects.requireNonNull(leafDecay, "leafDecay");
        Objects.requireNonNull(farmProtect, "farmProtect");
        Objects.requireNonNull(farmAssist, "farmAssist");
    }

    /** Resolve the survival config from the module's scoped {@link ConfigStore} ({@code modules.survival}). */
    public static SurvivalConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new SurvivalConfig(
                config.getBoolean("enabled", true),
                TreeFeller.from(config),
                Veinminer.from(config),
                LeafDecay.from(config),
                FarmProtect.from(config),
                FarmAssist.from(config));
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

    /**
     * The fast-leaf-decay mechanic under {@code leaf-decay { … }}: when a log is broken, the unsupported leaves within
     * {@code radius} are decayed a short delay later instead of waiting for vanilla's random-tick sweep.
     *
     * @param enabled whether fast-leaf-decay runs ({@code leaf-decay.enabled}, default {@code true})
     * @param radius the half-width of the cube around the broken log swept for leaves ({@code radius}, default 4)
     * @param delayTicks the tick delay before the sweep runs, so the cascade reads as a ripple ({@code delay-ticks},
     *     default 4)
     */
    public record LeafDecay(boolean enabled, int radius, int delayTicks) {

        public LeafDecay {
            radius = Math.max(1, radius);
            delayTicks = Math.max(0, delayTicks);
        }

        static LeafDecay from(ConfigStore config) {
            return new LeafDecay(
                    config.getBoolean("leaf-decay.enabled", true),
                    config.getInt("leaf-decay.radius", 4),
                    config.getInt("leaf-decay.delay-ticks", 4));
        }
    }

    /**
     * The farmland-protection mechanic under {@code farmprotect { … }}: a personal {@code /farmprotect} toggle that,
     * while on, stops the player trampling farmland into dirt. The mechanic carries no tuning of its own beyond its
     * enable gate — the per-player state lives in PDC.
     *
     * @param enabled whether farmprotect runs ({@code farmprotect.enabled}, default {@code true})
     */
    public record FarmProtect(boolean enabled) {

        static FarmProtect from(ConfigStore config) {
            return new FarmProtect(config.getBoolean("farmprotect.enabled", true));
        }
    }

    /**
     * The farm-assist mechanic under {@code farmassist { … }}: right-clicking a mature configured crop harvests its
     * drops and replants the same crop, spending one matching seed from the player's inventory.
     *
     * @param enabled whether farmassist runs ({@code farmassist.enabled}, default {@code true})
     * @param crops the crop block material ids farm-assist acts on ({@code crops}, default the common farmland crops)
     */
    public record FarmAssist(boolean enabled, List<String> crops) {

        /** The default crops — the four farmland crops and nether wart, each with a plantable seed. */
        private static final List<String> DEFAULT_CROPS =
                List.of("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART");

        public FarmAssist {
            Objects.requireNonNull(crops, "crops");
            crops = List.copyOf(crops);
        }

        static FarmAssist from(ConfigStore config) {
            return new FarmAssist(
                    config.getBoolean("farmassist.enabled", true),
                    config.getStringList("farmassist.crops", DEFAULT_CROPS));
        }
    }
}
