/**
 * The teleport context's domain events. {@link com.uxplima.uxmessentials.teleport.domain.event.TeleportEvent}
 * is the sealed per-context sub-interface of the shared {@code DomainEvent} marker; every concrete event
 * is a {@code record} it permits. The adapter bridges each to a Bukkit event so other plugins can
 * observe a teleport without importing this package.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.teleport.domain.event;
