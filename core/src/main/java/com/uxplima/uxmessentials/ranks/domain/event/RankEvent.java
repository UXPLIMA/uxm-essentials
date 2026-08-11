package com.uxplima.uxmessentials.ranks.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The ranks context's sealed family of domain events: the per-context seal that closes the event set without
 * touching the shared {@code DomainEvent} marker. Every concrete implementation is a {@code record}, and the
 * adapter bridges each to a Bukkit event so a permissions or chat plugin can follow a player up the ladder
 * without importing this package.
 *
 * <p>Names are past tense: a domain event records a move that already happened and is already durable.
 */
public sealed interface RankEvent extends DomainEvent permits PlayerRankedUp, PlayerRankSet, PlayerPrestiged {}
