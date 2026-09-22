package com.uxplima.uxmessentials.bootstrap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.plugin.PluginManager;

import org.jspecify.annotations.NullMarked;

/**
 * The command words this plugin shares with another plugin of ours, and which plugin owns each.
 *
 * <p>Every plugin of ours is sold alone, so each ships its own words, and this one ships almost every word
 * a server has. Where a dedicated plugin ships the same word, the dedicated plugin owns it: a server that
 * installed uxmShop next to this one wants {@code /sell} and {@code /worth} to be the shop's, and a server
 * without it wants them to be ours. Left to the server, two plugins registering one word are resolved by
 * load order; on the oneblock setup on 2026-09-22 {@code /sell} was the shop's and {@code /worth} was ours,
 * each reading its own price table.
 *
 * <p>This reads whether the other plugin is installed and nothing else: no call into it, no dependency and
 * no load order. The list is held to the other plugins' shipped {@code commands.conf} by
 * {@code scripts/check-style.sh}, so a word renamed there cannot leave a stale reservation here.
 */
@NullMarked
public final class FamilyCommandWords {

    /** Word, then the plugin that ships it. */
    private static final Map<String, String> OWNED = ownedWords();

    private FamilyCommandWords() {}

    /** The words whose owning plugin is installed on this server, keyed by word. */
    public static Map<String, String> reservedOn(PluginManager plugins) {
        Objects.requireNonNull(plugins, "plugins");
        Map<String, String> reserved = new LinkedHashMap<>();
        OWNED.forEach((word, owner) -> {
            if (plugins.getPlugin(owner) != null) {
                reserved.put(word, owner);
            }
        });
        return Map.copyOf(reserved);
    }

    private static Map<String, String> ownedWords() {
        Map<String, String> owned = new LinkedHashMap<>();
        owned.put("backup", "uxmBackup");
        owned.put("glow", "uxmGlow");
        owned.put("jump", "uxmParkour");
        owned.put("lang", "uxmLang");
        owned.put("motd", "uxmMOTD");
        owned.put("sell", "uxmShop");
        owned.put("worth", "uxmShop");
        return Map.copyOf(owned);
    }
}
