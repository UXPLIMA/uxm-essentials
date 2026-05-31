/**
 * The itemworld context's outbound adapters: the {@code com.uxplima.uxmessentials.audit}-channel
 * {@link com.uxplima.uxmessentials.itemworld.adapter.outbound.LoggingItemworldAudit} that emits the
 * abusable-verb audit trail (gated per-action by {@code itemworld.conf}), and the
 * {@link com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitItemResolver} anti-corruption boundary
 * that resolves the domain's normalised item/enchant/flag ids against the live Paper registries.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.itemworld.adapter.outbound;
