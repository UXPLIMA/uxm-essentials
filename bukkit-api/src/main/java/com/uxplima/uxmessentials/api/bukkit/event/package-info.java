/**
 * The Bukkit events uxmEssentials fires, one subpackage per bounded context.
 *
 * <p>Two shapes, and the name tells them apart. {@code Uxm<Context>Pre<Action>Event} extends
 * {@link com.uxplima.uxmessentials.api.bukkit.event.UxmCancellableEvent}: it asks before the action happens,
 * arrives asynchronously, and cancelling it blocks the action. {@code Uxm<Context><Action>Event} extends
 * {@link com.uxplima.uxmessentials.api.bukkit.event.UxmEvent}: it reports after the fact, arrives on a tick thread,
 * and cannot be cancelled because there is nothing left to cancel.
 *
 * <p>A disabled module fires neither.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.api.bukkit.event;
