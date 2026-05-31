/**
 * The messaging context's sealed {@code MessagingEvent} family and its concrete record events. The seal
 * lives on the per-context sub-interface, never on the shared {@code DomainEvent} marker; every concrete
 * implementation is a {@code record}, and the adapter bridges each to a Bukkit event so other plugins
 * observe messaging facts (a PM sent, mail delivered, a help-op raised) without importing this package.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.messaging.domain.event;
