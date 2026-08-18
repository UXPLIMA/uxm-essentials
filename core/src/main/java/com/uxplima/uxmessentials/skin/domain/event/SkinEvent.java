package com.uxplima.uxmessentials.skin.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The skin context's sealed family of domain events: the per-context seal that closes the event set without
 * touching the shared {@code DomainEvent} marker. Every concrete implementation is a {@code record}, and the
 * adapter bridges each to a Bukkit event so another plugin can observe a skin change without importing this
 * package.
 *
 * <p>Names are past tense: a domain event records something that already happened.
 */
public sealed interface SkinEvent extends DomainEvent permits SkinChanged, SkinCleared {}
