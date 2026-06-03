/**
 * The teleport context's outbound persistence adapter: the jOOQ {@link
 * com.uxplima.uxmessentials.persistence.teleport.JooqSpawnDirectory} over the generated V10 spawn tables (the
 * per-world spawn, the singleton main spawn, the named spawns and the per-world mirrors), the {@link
 * com.uxplima.uxmessentials.persistence.teleport.SpawnRows} anti-corruption mapping, and the {@link
 * com.uxplima.uxmessentials.persistence.teleport.SpawnDirectories} factory the bukkit-adapter wires from
 * without naming a jOOQ type. The vanilla-world last-resort fallback lives in the bukkit-adapter's decorator,
 * not here, so this module stays free of {@code org.bukkit}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.teleport;
