package com.uxplima.uxmessentials.teleport.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.teleport.domain.BiomeName;

/**
 * Resolves an operator-typed biome key against the server's biome registry, keeping the {@code /rtp biome <biome>}
 * use case free of any Bukkit {@code Registry}/{@code Biome} import. The adapter looks the key up in the live
 * registry and hands back a normalised {@link BiomeName} that matches the form a validated candidate carries, so
 * the biome gate compares like for like; an unknown key resolves to empty and the use case reports it to the player.
 */
public interface BiomeCatalog {

    /** The normalised {@link BiomeName} for {@code rawKey} (e.g. {@code plains}, {@code minecraft:desert}), or empty
     * when the server's registry has no such biome. */
    Optional<BiomeName> resolve(String rawKey);

    /** Every registered biome key, for the {@code /rtp biome} argument's tab completion. */
    List<String> keys();
}
