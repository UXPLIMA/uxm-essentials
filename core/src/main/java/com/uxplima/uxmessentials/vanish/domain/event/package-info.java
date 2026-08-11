/**
 * The vanish context's domain events: the sealed {@code VanishEvent} family and its one concrete record
 * ({@code VanishToggled}). The seal lives here, one level below the shared {@code DomainEvent} marker, so the
 * event set is closed per context; the adapter bridges it to a Bukkit event so other plugins hear a player go
 * hidden without importing this package or polling the store.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vanish.domain.event;
