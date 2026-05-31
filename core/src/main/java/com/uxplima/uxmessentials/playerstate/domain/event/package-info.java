/**
 * The playerstate context's domain events: the sealed {@code PlayerStateEvent} family and its concrete
 * records ({@code GodToggled}, {@code FlyToggled}, {@code GameModeChanged}, {@code SpeedChanged},
 * {@code Healed}, {@code Fed}). Each is a past-tense value object the use cases publish through the kernel's
 * {@code DomainEventPublisher} and the adapter bridges to a Bukkit event for the audit log and other plugins.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerstate.domain.event;
