package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Turns a resolved menu-icon material spec into a {@link BedrockImage} for a form button. Two shapes are handled:
 *
 * <ul>
 *   <li>A skull spec ({@code skull:<value>} / {@code head:<value>}, case-insensitive) → an {@code mc-heads.net}
 *       avatar URL the Bedrock client fetches. {@code self} is the viewer; a plain player name or a UUID becomes
 *       {@code https://mc-heads.net/avatar/<who>}. A base64 texture, a skin URL, or a {@code basehead:} spec has no
 *       look-up key mc-heads understands, so it yields {@code null} — best-effort, the button just shows no icon.
 *   <li>Any other spec is treated as a Bukkit material name → a best-effort Bedrock resource path
 *       {@code textures/<category>/<name>}. The name is lowercased; spawn eggs and music discs get the Bedrock stem
 *       remap ({@code spawn_<mob>} / {@code record_<name>}); the category is a small items-vs-blocks heuristic. AIR
 *       and a blank name have no icon → {@code null}.
 * </ul>
 *
 * <p>The material path is <em>best-effort</em>: this maps Java material names to Bedrock paths by a lowercase-plus-
 * heuristic rule, not the exact Bedrock ID table, so an irregular block (a coloured wool, a terracotta) may land on a
 * path that does not exist. That is harmless — the Bedrock client renders a fallback icon when a path misses, rather
 * than erroring. The {@code mc-heads} URL is likewise a plain string the client loads; the server makes no HTTP call.
 * This type is pure: it names no Bukkit or Cumulus class and does no I/O.
 */
public final class BedrockIcons {

    private BedrockIcons() {}

    private static final String SKULL = "skull:";
    private static final String HEAD = "head:";
    private static final String BASEHEAD = "basehead:";
    private static final String SELF = "self";
    private static final String AVATAR = "https://mc-heads.net/avatar/";
    private static final String SPAWN_EGG = "_SPAWN_EGG";
    private static final String MUSIC_DISC = "MUSIC_DISC_";

    // Material names with no icon: a form button for one of these gets no image.
    private static final Set<String> AIR = Set.of("AIR", "CAVE_AIR", "VOID_AIR");

    // A material is a block if it wears one of these suffixes (else an item). A best-effort split — the client shows
    // a fallback for a miss — so an exhaustive table is unnecessary; these cover the common decorative/build blocks.
    private static final Set<String> BLOCK_SUFFIXES = Set.of(
            "_BLOCK",
            "_ORE",
            "_LOG",
            "_WOOD",
            "_PLANKS",
            "_LEAVES",
            "_SAPLING",
            "_WOOL",
            "_STAINED_GLASS",
            "_GLASS",
            "_GLASS_PANE",
            "_CARPET",
            "_CONCRETE",
            "_CONCRETE_POWDER",
            "_TERRACOTTA",
            "_STAIRS",
            "_SLAB",
            "_FENCE",
            "_FENCE_GATE",
            "_WALL",
            "_DOOR",
            "_TRAPDOOR",
            "_BUTTON",
            "_PRESSURE_PLATE",
            "_BED",
            "_BANNER",
            "_SIGN",
            "_BRICKS");

    // Common single-word blocks the suffix rule alone would misfile as items.
    private static final Set<String> EXACT_BLOCKS = Set.of(
            "STONE",
            "DIRT",
            "COBBLESTONE",
            "GRAVEL",
            "SAND",
            "GRASS_BLOCK",
            "BEDROCK",
            "OBSIDIAN",
            "GLASS",
            "GLOWSTONE",
            "NETHERRACK",
            "BOOKSHELF",
            "SPONGE",
            "TNT",
            "ICE",
            "SNOW",
            "CLAY",
            "PUMPKIN",
            "MELON",
            "CHEST",
            "FURNACE",
            "CRAFTING_TABLE");

    /**
     * The {@link BedrockImage} for a resolved material spec, or {@code null} for a spec with no usable icon (AIR, a
     * blank name, a {@code basehead:} or base64/URL skull value).
     *
     * @param spec the resolved icon material spec — a material name, or a {@code skull:}/{@code head:} value; never
     *     {@code null}
     * @param viewerUuid the viewer's UUID, used to resolve a {@code self} head; never {@code null}
     * @return the button image, or {@code null} when the spec has no icon this can source
     */
    public static @Nullable BedrockImage forMaterialSpec(String spec, UUID viewerUuid) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(viewerUuid, "viewerUuid");
        String trimmed = spec.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith(BASEHEAD)) {
            return null;
        }
        if (lower.startsWith(SKULL)) {
            return skullUrl(trimmed.substring(SKULL.length()), viewerUuid);
        }
        if (lower.startsWith(HEAD)) {
            return skullUrl(trimmed.substring(HEAD.length()), viewerUuid);
        }
        return materialPath(trimmed);
    }

    /**
     * The mc-heads avatar URL for a {@code skull:}/{@code head:} value: {@code self} is the viewer, a plain name or
     * UUID is looked up by itself, and anything else (a base64 texture, a skin URL) falls through to {@code null}.
     */
    private static @Nullable BedrockImage skullUrl(String value, UUID viewerUuid) {
        String who = value.trim();
        if (who.isBlank()) {
            return null;
        }
        if (who.equalsIgnoreCase(SELF)) {
            return new BedrockImage(BedrockImage.Kind.URL, AVATAR + viewerUuid);
        }
        if (isNameOrUuid(who)) {
            return new BedrockImage(BedrockImage.Kind.URL, AVATAR + who);
        }
        return null;
    }

    /**
     * Whether {@code who} is a plain player name or a UUID (dashed or not) — the only shapes mc-heads can resolve. A
     * value carrying {@code /} (a skin URL) or longer than a dashed UUID (a base64 texture blob) is neither.
     */
    private static boolean isNameOrUuid(String who) {
        if (who.indexOf('/') >= 0 || who.length() > 36) {
            return false;
        }
        return who.matches("[A-Za-z0-9_-]+");
    }

    /**
     * The best-effort Bedrock texture path for a bare material name, or {@code null} for AIR/blank. Spawn eggs and
     * music discs take their Bedrock stem remap; every other name lowercases as-is under its item/block category.
     */
    private static @Nullable BedrockImage materialPath(String spec) {
        String name = spec.trim().toUpperCase(Locale.ROOT);
        if (name.isEmpty() || AIR.contains(name)) {
            return null;
        }
        String remap = spawnEggOrDisc(name);
        if (remap != null) {
            return new BedrockImage(BedrockImage.Kind.PATH, "textures/items/" + remap);
        }
        String path = "textures/" + category(name) + "/" + name.toLowerCase(Locale.ROOT);
        return new BedrockImage(BedrockImage.Kind.PATH, path);
    }

    /** A spawn egg → {@code spawn_<mob>}, a music disc → {@code record_<name>}, else {@code null} (no remap). */
    private static @Nullable String spawnEggOrDisc(String name) {
        if (name.endsWith(SPAWN_EGG)) {
            return "spawn_"
                    + name.substring(0, name.length() - SPAWN_EGG.length()).toLowerCase(Locale.ROOT);
        }
        if (name.startsWith(MUSIC_DISC)) {
            return "record_" + name.substring(MUSIC_DISC.length()).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    /** {@code blocks} for a curated block or a block-suffixed name, else {@code items}. */
    private static String category(String name) {
        if (EXACT_BLOCKS.contains(name)) {
            return "blocks";
        }
        for (String suffix : BLOCK_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return "blocks";
            }
        }
        return "items";
    }
}
