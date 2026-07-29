package com.uxplima.uxmessentials.migration.convert.multiverse.parse;

import java.util.Objects;
import java.util.Optional;

/**
 * One world entry as it stands in Multiverse's {@code worlds.yml}, in the competitor's own vocabulary: every field
 * optional because Multiverse omits anything left at its default, and every field a plain Java type because no
 * domain type may appear in {@code parse/} (docs/12-migration §2). The mapper is what turns this into a
 * {@code ManagedWorld}.
 *
 * <p>Only what Multiverse actually stores and we have a home for is carried. Its per-world weather, hunger,
 * auto-heal, bed-respawn, portal-form, scale, respawn-world, world-blacklist and biome settings have no counterpart
 * in our worlds module and are deliberately not represented here; they are documented as not-migrated rather than
 * half-mapped onto a setting that means something else.
 *
 * @param name the world's folder name, which is also Multiverse's registry key
 * @param alias the display alias, absent when Multiverse holds the default empty one
 * @param environment the Bukkit environment name ({@code NORMAL}, {@code NETHER}, {@code THE_END})
 * @param seed the generation seed, absent when Multiverse holds its "no seed" sentinel
 * @param generator the external generator reference in {@code plugin[:args]} form
 * @param difficulty the Bukkit difficulty name
 * @param pvp whether PvP is allowed in the world
 * @param autoLoad whether Multiverse loads the world at startup
 * @param playerLimit Multiverse's player cap; its {@code -1} means "no limit"
 * @param gameMode the gamemode Multiverse forces on entry
 * @param entryFee the money charged on entry, present only when the fee is money rather than an item
 * @param spawn the stored world spawn
 */
public record MultiverseWorld(
        String name,
        Optional<String> alias,
        Optional<String> environment,
        Optional<Long> seed,
        Optional<String> generator,
        Optional<String> difficulty,
        Optional<Boolean> pvp,
        Optional<Boolean> autoLoad,
        Optional<Integer> playerLimit,
        Optional<String> gameMode,
        Optional<Double> entryFee,
        Optional<MultiverseSpawn> spawn) {

    public MultiverseWorld {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(pvp, "pvp");
        Objects.requireNonNull(autoLoad, "autoLoad");
        Objects.requireNonNull(playerLimit, "playerLimit");
        Objects.requireNonNull(gameMode, "gameMode");
        Objects.requireNonNull(entryFee, "entryFee");
        Objects.requireNonNull(spawn, "spawn");
    }
}
