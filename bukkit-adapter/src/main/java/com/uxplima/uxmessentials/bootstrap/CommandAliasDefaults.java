package com.uxplima.uxmessentials.bootstrap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.command.CommandDefinition;

/**
 * The curated muscle-memory aliases we ship turned on by default ({@code /m}, {@code /tell}, {@code /v},
 * {@code /gm}, {@code /h}, …). These augment each command's code-side defaults before the catalog
 * resolves the operator's overrides, so the short forms people reach for from other plugins answer out
 * of the box without anyone editing {@code commands/<module>.conf}.
 *
 * <p>The standalone commands ({@code homes}/{@code warps}/{@code kits} and the time/weather literals)
 * are deliberately never produced here: they are real commands in their own right, so aliasing them onto
 * {@code home}/{@code warp}/{@code kit}/{@code time}/{@code weather} would steal a name the resolver
 * would then have to drop. {@link #CONFLICT_TARGETS} backstops that intent — anything that lands on one
 * of those tokens is filtered out rather than silently shipped.
 */
public final class CommandAliasDefaults {

    private CommandAliasDefaults() {}

    // Keyed by commandId; values are the extra aliases to fold in. Only ids that exist as real top-level
    // command literals appear, and only aliases justified by the design spec's named list. Entries whose
    // alias already matches a code default (balance->money, eco->economy) are deduped on merge.
    private static final Map<String, List<String>> EXTRA = Map.of(
            "msg", List.of("m", "tell", "whisper", "w"),
            "reply", List.of("r"),
            "vanish", List.of("v"),
            "balance", List.of("bal", "money"),
            "eco", List.of("economy"),
            "gamemode", List.of("gm"),
            "home", List.of("h"),
            "warp", List.of("wp"),
            "kit", List.of("k"));

    // Real standalone commands that must keep their own literals; never emitted as an added alias.
    private static final Set<String> CONFLICT_TARGETS =
            Set.of("homes", "warps", "kits", "day", "night", "sun", "rain", "thunder", "walkspeed", "flyspeed");

    /**
     * Returns a copy of {@code defs} where every command with a curated entry has those aliases folded
     * into its defaults. Code defaults come first and win on ordering; curated extras follow, deduped;
     * any token that would collide with a standalone command is dropped. Original order is preserved and
     * commands without an entry are returned unchanged.
     */
    public static List<CommandDefinition> augment(List<CommandDefinition> defs) {
        Objects.requireNonNull(defs, "defs");
        List<CommandDefinition> out = new ArrayList<>(defs.size());
        for (CommandDefinition def : defs) {
            List<String> extra = EXTRA.get(def.id().value());
            out.add(extra == null ? def : new CommandDefinition(def.id(), def.defaultName(), merge(def, extra)));
        }
        return List.copyOf(out);
    }

    private static List<String> merge(CommandDefinition def, List<String> extra) {
        Set<String> merged = new LinkedHashSet<>(def.defaultAliases());
        for (String alias : extra) {
            if (!CONFLICT_TARGETS.contains(alias)) {
                merged.add(alias);
            }
        }
        return List.copyOf(merged);
    }
}
