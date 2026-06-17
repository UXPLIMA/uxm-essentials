package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.DyeColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The per-entity-type appearance variants beyond the baby/size/charged/villager core of {@link NpcTypeData}: a
 * horse's coat colour and markings, a llama/parrot/axolotl coat variant, a fox/rabbit type, a sheep/wolf/shulker
 * colour, a shulker's peek, a panda's gene, and the goat/allay/piglin/camel/bee/vex state flags. Kept in its own class so
 * {@code NpcTypeData} stays focused; the same support-map correctness invariant holds — a value is sent only to the
 * one Bukkit type that carries that field, and an unsupported key or unparseable value is skipped fail-soft (logged
 * at debug), never thrown on the render thread.
 *
 * <p>Most variants are a bounded integer on one type, so a small {@link IntVariant} table drives them uniformly; the
 * dye-coloured ones (sheep wool, wolf collar, shulker shell) share a {@link ColorVariant} table that accepts a
 * {@link DyeColor} name or the raw 0–15 id alike; and the boolean states (goat screaming, allay/piglin dancing, camel
 * dash) share a {@link BoolVariant} table. The horse is the one special case: it packs a colour and a marking into
 * one packet (with {@code horse_style} a friendly {@link Horse.Style}-named alias for the marking id). The
 * by-name dynamic-registry variants (cat, frog) live in {@link NpcNameVariantData}; this class delegates the
 * known-key, validity, and apply paths to it so the two stay focused.
 */
@NullMarked
final class NpcVariantData {

    static final String KEY_HORSE_COLOR = "horse_color";
    static final String KEY_HORSE_MARKINGS = "horse_markings";
    static final String KEY_HORSE_STYLE = "horse_style";
    static final String KEY_LLAMA_VARIANT = "llama_variant";
    static final String KEY_SHEEP_COLOR = "sheep_color";
    static final String KEY_WOLF_COLLAR = "wolf_collar";
    static final String KEY_SHULKER_COLOR = "shulker_color";
    static final String KEY_SHULKER_PEEK = "shulker_peek";
    static final String KEY_PANDA_GENE = "panda_gene";
    static final String KEY_PARROT_VARIANT = "parrot_variant";
    static final String KEY_AXOLOTL_VARIANT = "axolotl_variant";
    static final String KEY_FOX_TYPE = "fox_type";
    static final String KEY_RABBIT_TYPE = "rabbit_type";
    static final String KEY_GOAT_SCREAMING = "goat_screaming";
    static final String KEY_ALLAY_DANCING = "allay_dancing";
    static final String KEY_PIGLIN_DANCING = "piglin_dancing";
    static final String KEY_CAMEL_DASH = "camel_dash";
    static final String KEY_BEE_NECTAR = "bee_nectar";
    static final String KEY_VEX_CHARGING = "vex_charging";
    static final String KEY_TROPICAL_FISH = "tropical_fish";

    /** The horse coat colours (0–6) and body markings (0–4); the two pack into one variant integer. */
    private static final int MAX_HORSE_COLOR = 6;

    private static final int MAX_HORSE_MARKINGS = 4;
    /** The highest wool colour id; a {@link DyeColor} name resolves to its wool id, which never exceeds this. */
    private static final int MAX_DYE_COLOR = 15;
    /** The killer (toast) rabbit's wire type id — valid even though it sits outside the 0–5 coat run. */
    private static final int RABBIT_KILLER = 99;
    /** A shulker shell opens from 0 (closed) to 100 (fully open). */
    private static final int MAX_SHULKER_PEEK = 100;
    /** The seven panda genes (0–6: normal, lazy, worried, playful, brown, weak, aggressive). */
    private static final int MAX_PANDA_GENE = 6;
    /** The highest index into the server's 22 predefined common tropical-fish variants. */
    private static final int MAX_TROPICAL_FISH_VARIANT = 21;

    /**
     * The single-key bounded-int variants: each is one Bukkit type, an inclusive max, and the lib method that
     * ships the value. The horse (two keys, one packet) and the sheep (name-or-id) are handled separately.
     */
    private static final List<IntVariant> INT_VARIANTS = List.of(
            new IntVariant(KEY_LLAMA_VARIANT, EntityType.LLAMA, 3, NpcPackets::llamaVariant),
            new IntVariant(KEY_PARROT_VARIANT, EntityType.PARROT, 4, NpcPackets::parrotVariant),
            new IntVariant(KEY_AXOLOTL_VARIANT, EntityType.AXOLOTL, 4, NpcPackets::axolotlVariant),
            new IntVariant(KEY_FOX_TYPE, EntityType.FOX, 1, NpcPackets::foxType),
            new IntVariant(KEY_SHULKER_PEEK, EntityType.SHULKER, MAX_SHULKER_PEEK, NpcPackets::shulkerPeek),
            new IntVariant(KEY_PANDA_GENE, EntityType.PANDA, MAX_PANDA_GENE, NpcPackets::pandaGene),
            new IntVariant(
                    KEY_TROPICAL_FISH,
                    EntityType.TROPICAL_FISH,
                    MAX_TROPICAL_FISH_VARIANT,
                    NpcPackets::tropicalFishVariant));

    /**
     * The single-key dye-colour variants: each is one Bukkit type and the lib method that ships a 0–15 colour id,
     * accepting a {@link DyeColor} name or the raw id alike. Folded into one table because the sheep wool, wolf
     * collar, and shulker shell colours share the exact apply and validation path.
     */
    private static final List<ColorVariant> COLOR_VARIANTS = List.of(
            new ColorVariant(KEY_SHEEP_COLOR, EntityType.SHEEP, NpcPackets::sheepColor),
            new ColorVariant(KEY_WOLF_COLLAR, EntityType.WOLF, NpcPackets::wolfCollar),
            new ColorVariant(KEY_SHULKER_COLOR, EntityType.SHULKER, NpcPackets::shulkerColor));

    /** The single-key boolean state variants: each is one Bukkit type and the lib method that ships a true/false. */
    private static final List<BoolVariant> BOOL_VARIANTS = List.of(
            new BoolVariant(KEY_GOAT_SCREAMING, EntityType.GOAT, NpcPackets::goatScreaming),
            new BoolVariant(KEY_ALLAY_DANCING, EntityType.ALLAY, NpcPackets::allayDancing),
            new BoolVariant(KEY_PIGLIN_DANCING, EntityType.PIGLIN, NpcPackets::piglinDancing),
            new BoolVariant(KEY_CAMEL_DASH, EntityType.CAMEL, NpcPackets::camelDash),
            new BoolVariant(KEY_BEE_NECTAR, EntityType.BEE, NpcPackets::beeNectar),
            new BoolVariant(KEY_VEX_CHARGING, EntityType.VEX, NpcPackets::vexCharging));

    private NpcVariantData() {}

    /**
     * Send {@code npc}'s supported variant data to {@code viewer} for the fake entity {@code entityId}, branching
     * on the resolved Bukkit {@code type}. Every property is fail-soft: an unsupported or unparseable one is
     * skipped (logged at debug), never thrown.
     */
    static void apply(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        applyHorse(packets, viewer, id, type, data, npc, log);
        for (ColorVariant variant : COLOR_VARIANTS) {
            applyColorVariant(packets, viewer, id, type, data, npc, log, variant);
        }
        applyRabbit(packets, viewer, id, type, data, npc, log);
        for (IntVariant variant : INT_VARIANTS) {
            applyIntVariant(packets, viewer, id, type, data, npc, log, variant);
        }
        for (BoolVariant variant : BOOL_VARIANTS) {
            applyBoolVariant(packets, viewer, id, type, data, npc, log, variant);
        }
        NpcNameVariantData.apply(packets, viewer, id, type, data, npc, log);
    }

    private static void applyHorse(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        if (!data.containsKey(KEY_HORSE_COLOR)
                && !data.containsKey(KEY_HORSE_MARKINGS)
                && !data.containsKey(KEY_HORSE_STYLE)) {
            return;
        }
        if (type != EntityType.HORSE) {
            skip(log, npc, KEY_HORSE_COLOR, type, "type is not a horse");
            return;
        }
        int color = clampInt(data.get(KEY_HORSE_COLOR), MAX_HORSE_COLOR);
        int markings = resolveMarkings(data);
        packets.send(viewer, packets.horseVariant(id, color, markings));
    }

    private static void applyColorVariant(
            NpcPackets packets,
            Player viewer,
            int id,
            EntityType type,
            Map<String, String> data,
            Npc npc,
            Logger log,
            ColorVariant variant) {
        String value = data.get(variant.key());
        if (value == null) {
            return;
        }
        if (type != variant.type()) {
            skip(
                    log,
                    npc,
                    variant.key(),
                    type,
                    "type is not a " + variant.type().name().toLowerCase(Locale.ROOT));
            return;
        }
        Integer color = parseColorId(value);
        if (color == null) {
            skip(log, npc, variant.key(), type, "value is not a dye colour or 0-15 id: " + value);
            return;
        }
        variant.send().accept(packets, viewer, id, color);
    }

    private static void applyRabbit(
            NpcPackets packets, Player viewer, int id, EntityType type, Map<String, String> data, Npc npc, Logger log) {
        String value = data.get(KEY_RABBIT_TYPE);
        if (value == null) {
            return;
        }
        if (type != EntityType.RABBIT) {
            skip(log, npc, KEY_RABBIT_TYPE, type, "type is not a rabbit");
            return;
        }
        Integer parsed = parseInt(value);
        if (parsed == null || !isRabbitType(parsed)) {
            skip(log, npc, KEY_RABBIT_TYPE, type, "value is not a 0-5 coat or 99 (killer): " + value);
            return;
        }
        packets.send(viewer, packets.rabbitType(id, parsed));
    }

    private static void applyIntVariant(
            NpcPackets packets,
            Player viewer,
            int id,
            EntityType type,
            Map<String, String> data,
            Npc npc,
            Logger log,
            IntVariant variant) {
        String value = data.get(variant.key());
        if (value == null) {
            return;
        }
        if (type != variant.type()) {
            skip(
                    log,
                    npc,
                    variant.key(),
                    type,
                    "type is not a " + variant.type().name().toLowerCase(Locale.ROOT));
            return;
        }
        Integer parsed = parseInt(value);
        if (parsed == null || parsed < 0 || parsed > variant.max()) {
            skip(log, npc, variant.key(), type, "value is out of range 0-" + variant.max() + ": " + value);
            return;
        }
        variant.send().accept(packets, viewer, id, parsed);
    }

    private static void applyBoolVariant(
            NpcPackets packets,
            Player viewer,
            int id,
            EntityType type,
            Map<String, String> data,
            Npc npc,
            Logger log,
            BoolVariant variant) {
        String value = data.get(variant.key());
        if (value == null) {
            return;
        }
        if (type != variant.type()) {
            skip(
                    log,
                    npc,
                    variant.key(),
                    type,
                    "type is not a " + variant.type().name().toLowerCase(Locale.ROOT));
            return;
        }
        Boolean parsed = parseBool(value);
        if (parsed == null) {
            skip(log, npc, variant.key(), type, "value is not true or false: " + value);
            return;
        }
        variant.send().accept(packets, viewer, id, parsed);
    }

    /** Whether {@code key} is one of the variant keys this class applies — the set the command validates against. */
    static boolean isKnownKey(String key) {
        return KEYS.contains(key.toLowerCase(Locale.ROOT)) || NpcNameVariantData.isKnownKey(key);
    }

    /**
     * Whether {@code value} is valid for the (already-known) variant {@code key}: a 0–max integer for the bounded
     * coats/types, a 0–5 coat or 99 for the rabbit, and a {@link DyeColor} name or a 0–15 id for the sheep colour.
     */
    static boolean isValidValue(String key, String value) {
        String lower = key.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case KEY_HORSE_COLOR -> isInRange(value, MAX_HORSE_COLOR);
            case KEY_HORSE_MARKINGS -> isInRange(value, MAX_HORSE_MARKINGS);
            case KEY_HORSE_STYLE -> parseStyle(value) != null;
            case KEY_SHEEP_COLOR, KEY_WOLF_COLLAR, KEY_SHULKER_COLOR -> parseColorId(value) != null;
            case KEY_GOAT_SCREAMING,
                    KEY_ALLAY_DANCING,
                    KEY_PIGLIN_DANCING,
                    KEY_CAMEL_DASH,
                    KEY_BEE_NECTAR,
                    KEY_VEX_CHARGING -> parseBool(value) != null;
            case KEY_RABBIT_TYPE -> {
                Integer parsed = parseInt(value);
                yield parsed != null && isRabbitType(parsed);
            }
            default -> {
                if (NpcNameVariantData.isKnownKey(lower)) {
                    yield NpcNameVariantData.isValidValue(lower, value);
                }
                IntVariant variant = byKey(lower);
                yield variant != null && isInRange(value, variant.max());
            }
        };
    }

    private static boolean isInRange(String value, int max) {
        Integer parsed = parseInt(value);
        return parsed != null && parsed >= 0 && parsed <= max;
    }

    private static boolean isRabbitType(int value) {
        return (value >= 0 && value <= 5) || value == RABBIT_KILLER;
    }

    /** Clamp a stored int into {@code 0..max}, defaulting a missing or unparseable value to 0 (the first variant). */
    private static int clampInt(@Nullable String value, int max) {
        Integer parsed = parseInt(value);
        return parsed == null ? 0 : Math.clamp(parsed, 0, max);
    }

    /**
     * The horse body markings 0–4 to ship: a {@link Horse.Style} name ({@code horse_style}) when one is set (its
     * ordinal is the wire markings id), else the raw {@code horse_markings} integer. The style key is the friendly
     * alias for the same field, so when both are present the named style wins.
     */
    private static int resolveMarkings(Map<String, String> data) {
        Integer style = parseStyle(data.get(KEY_HORSE_STYLE));
        return style != null ? style : clampInt(data.get(KEY_HORSE_MARKINGS), MAX_HORSE_MARKINGS);
    }

    /** A {@link Horse.Style} name resolved to its 0–4 markings id, or {@code null} when absent or not a style name. */
    private static @Nullable Integer parseStyle(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return markingsOf(Horse.Style.valueOf(value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notAStyle) {
            return null;
        }
    }

    /** The wire markings id for a horse style — an explicit map (not the enum ordinal, which Error Prone flags). */
    private static int markingsOf(Horse.Style style) {
        return switch (style) {
            case NONE -> 0;
            case WHITE -> 1;
            case WHITEFIELD -> 2;
            case WHITE_DOTS -> 3;
            case BLACK_DOTS -> 4;
        };
    }

    /**
     * Resolve a sheep colour to its 0–15 wool id from either a {@link DyeColor} name (e.g. {@code red}) or a raw
     * id, or {@code null} when the value is neither — the fail-soft signal the apply and validation paths share.
     */
    private static @Nullable Integer parseColorId(String value) {
        String trimmed = value.strip();
        Integer raw = parseInt(trimmed);
        if (raw != null) {
            return raw >= 0 && raw <= MAX_DYE_COLOR ? raw : null;
        }
        try {
            return (int) DyeColor.valueOf(trimmed.toUpperCase(Locale.ROOT)).getWoolData();
        } catch (IllegalArgumentException notADyeColor) {
            return null;
        }
    }

    /** Parse a strict {@code true}/{@code false} (case-insensitive), or {@code null} for anything else. */
    private static @Nullable Boolean parseBool(String value) {
        String trimmed = value.strip().toLowerCase(Locale.ROOT);
        return switch (trimmed) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static @Nullable Integer parseInt(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException notAnInt) {
            return null;
        }
    }

    private static @Nullable IntVariant byKey(String key) {
        for (IntVariant variant : INT_VARIANTS) {
            if (variant.key().equals(key)) {
                return variant;
            }
        }
        return null;
    }

    private static void skip(Logger log, Npc npc, String key, EntityType type, String reason) {
        log.debug("NPC {} skips variant-data {} on a {} NPC ({})", npc.name().value(), key, type.name(), reason);
    }

    /** A single-key bounded-int variant: its key, the one type that carries it, the inclusive max, and its packet. */
    private record IntVariant(String key, EntityType type, int max, IntVariantSender send) {}

    /** A single-key dye-colour variant: its key, the one type that carries it, and the lib packet it ships. */
    private record ColorVariant(String key, EntityType type, IntVariantSender send) {}

    /** A single-key boolean state variant: its key, the one type that carries it, and the lib packet it ships. */
    private record BoolVariant(String key, EntityType type, BoolVariantSender send) {}

    /** A four-argument send hook bound to a lib {@code (entityId, boolean)} variant method via a method reference. */
    @FunctionalInterface
    private interface BoolVariantSender {
        Object build(NpcPackets packets, int entityId, boolean value);

        default void accept(NpcPackets packets, Player viewer, int entityId, boolean value) {
            packets.send(viewer, build(packets, entityId, value));
        }
    }

    /** A four-argument send hook bound to a lib {@code (entityId, int)} variant method via a method reference. */
    @FunctionalInterface
    private interface IntVariantSender {
        Object build(NpcPackets packets, int entityId, int value);

        default void accept(NpcPackets packets, Player viewer, int entityId, int value) {
            packets.send(viewer, build(packets, entityId, value));
        }
    }

    /** The full set of variant keys, lower-case, for the known-key check (the table keys plus the special ones). */
    private static final Set<String> KEYS = Set.of(
            KEY_HORSE_COLOR,
            KEY_HORSE_MARKINGS,
            KEY_HORSE_STYLE,
            KEY_LLAMA_VARIANT,
            KEY_SHEEP_COLOR,
            KEY_WOLF_COLLAR,
            KEY_SHULKER_COLOR,
            KEY_SHULKER_PEEK,
            KEY_PANDA_GENE,
            KEY_PARROT_VARIANT,
            KEY_AXOLOTL_VARIANT,
            KEY_FOX_TYPE,
            KEY_RABBIT_TYPE,
            KEY_GOAT_SCREAMING,
            KEY_ALLAY_DANCING,
            KEY_PIGLIN_DANCING,
            KEY_CAMEL_DASH,
            KEY_BEE_NECTAR,
            KEY_VEX_CHARGING,
            KEY_TROPICAL_FISH);
}
