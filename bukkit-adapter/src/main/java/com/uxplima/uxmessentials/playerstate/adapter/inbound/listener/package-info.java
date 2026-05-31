/**
 * The playerstate context's Bukkit listener: {@code PlayerStateListener} re-applies a player's
 * {@code PlayerStateSnapshot} on join and respawn (a death resets {@code allowFlight} and invulnerability) and
 * drops it on quit. Reconciliation routes through the {@code StateReconciler} so the Bukkit mutation runs on
 * the player's owning region thread via the {@code Scheduler} port.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerstate.adapter.inbound.listener;
