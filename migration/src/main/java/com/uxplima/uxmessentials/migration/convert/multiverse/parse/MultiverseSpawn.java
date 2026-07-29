package com.uxplima.uxmessentials.migration.convert.multiverse.parse;

/**
 * A world spawn as Multiverse stores it: the five numeric components of a Bukkit location with the world implied by
 * the entry it sits under. Kept as raw numbers rather than a domain position so the parse layer stays free of any
 * domain type (docs/12-migration §2).
 */
public record MultiverseSpawn(double x, double y, double z, float yaw, float pitch) {}
