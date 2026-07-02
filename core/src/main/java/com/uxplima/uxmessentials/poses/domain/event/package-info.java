/**
 * The poses context's domain events: the sealed {@link com.uxplima.uxmessentials.poses.domain.event.PoseEvent}
 * family and its record implementations {@link com.uxplima.uxmessentials.poses.domain.event.PoseStarted} /
 * {@link com.uxplima.uxmessentials.poses.domain.event.PoseEnded}. The seal closes the event set for this context
 * without touching the shared {@code DomainEvent} marker; the adapter bridges each to a cancellable Bukkit event.
 * Pure Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.poses.domain.event;

import org.jspecify.annotations.NullMarked;
