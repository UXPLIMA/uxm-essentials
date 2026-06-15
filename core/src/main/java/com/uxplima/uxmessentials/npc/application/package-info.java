/**
 * Application layer of the npc bounded context: the {@code NpcModule} feature-module registration, the single
 * {@code /npc} command surface, the create / delete / list / move / skin / command use cases, the
 * {@code NpcNotifier} send surface, the {@code NpcMessageKey} catalog handles, and the {@code NpcRepository} /
 * {@code NpcView} outbound ports (under {@code port/}). The use cases orchestrate the {@code Npc} aggregate
 * through those ports and never touch Bukkit, Paper, Kyori, or logging types — the adapters supply the
 * implementations.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.npc.application;
