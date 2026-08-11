package com.uxplima.uxmessentials.invrollback.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The inventory-rollback context's sealed family of domain events.
 *
 * <p>One member, and deliberately. A capture happens constantly and by itself: every death and every logout takes
 * one, and an event for each would be noise nobody could act on. A restore is the rare, deliberate act that
 * overwrites what a player is holding, and that is worth telling other plugins about.
 */
public sealed interface InvrollbackEvent extends DomainEvent permits SnapshotRestored {}
