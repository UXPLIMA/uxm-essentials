/**
 * The playerstate context's use cases and outbound ports. The toggle/set use cases ({@code ToggleGod},
 * {@code ToggleFly}, {@code SetGamemode}, {@code SetSpeed}) mutate the immutable {@code PlayerStateSnapshot}
 * atomically through the {@code PlayerStateStore} port and push the new value to the live player through the
 * {@code StateReconciler} port (which runs on the player's owning region thread); the apply-once/live-only
 * verbs ({@code Heal}, {@code Feed}, {@code Extinguish}, {@code Suicide}, {@code ToggleNightVision},
 * {@code SetPersonalTime}, {@code SetPersonalWeather}) act through the {@code PlayerEffects} port, and
 * {@code ListNearby} reads the {@code NearbyPlayers} port. Feedback renders through the {@code Messages}/
 * {@code MessageSink} pair via {@code PlayerStateNotifier}, and state changes publish through the kernel's
 * {@code DomainEventPublisher}. The {@code PlayerstateModule} declares the context's commands and enable gate.
 * No Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerstate.application;
