/**
 * The communication context's sealed domain-event family. {@code CommunicationEvent} is the per-context seal over
 * the shared {@code DomainEvent} marker; its concrete members are records ({@code BroadcastOptOutToggled} when a
 * player flips whether they receive announcer broadcasts, {@code AnnouncerReloaded} when the operator reloads the
 * schedule). The adapter bridges each to a Bukkit event so other plugins observe the change without importing
 * this package. No Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.communication.domain.event;
