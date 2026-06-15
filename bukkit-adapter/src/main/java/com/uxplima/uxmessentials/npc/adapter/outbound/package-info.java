/**
 * The npc context's outbound adapters: the {@code NpcRenderer} that realises the {@code NpcView} port over the
 * uxmLib fake-player packet stack (per-viewer spawn/tab-hide/remove with no real entity, range-culled and
 * region-thread-correct), the {@code RenderedNpc} runtime pairing of an NPC with its stable profile uuid and
 * entity id, and the {@code BukkitNpcSkins} reader that lifts a player's skin out of their Bukkit profile.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.npc.adapter.outbound;
