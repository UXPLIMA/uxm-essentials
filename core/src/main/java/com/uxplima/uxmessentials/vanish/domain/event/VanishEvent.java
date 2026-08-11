package com.uxplima.uxmessentials.vanish.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The vanish context's sealed family of domain events, the per-context seal that closes the event set without
 * touching the shared {@code DomainEvent} marker. Every concrete implementation is a {@code record}, and the
 * adapter bridges each to a Bukkit event so other plugins observe a player going hidden without importing this
 * package.
 *
 * <p>One fact only, because vanish has one thing to say: somebody is hidden now, or they are not.
 */
public sealed interface VanishEvent extends DomainEvent permits VanishToggled {}
