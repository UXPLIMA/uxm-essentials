/**
 * The vaults context's domain events: the sealed {@code VaultEvent} family and its concrete records
 * ({@code VaultOpened}, {@code VaultContentsChanged}). The seal lives here, one level below the shared
 * {@code DomainEvent} marker, so the event set is closed per context; the adapter bridges each to a Bukkit
 * event so other plugins observe vault activity without importing this package.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vaults.domain.event;
