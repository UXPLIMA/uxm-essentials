/**
 * The transient per-player meta accessor for the menu engine: a thin, typed wrapper over an online player's
 * {@link org.bukkit.persistence.PersistentDataContainer}. The Phase-3 {@code has-meta} requirement and the Phase-7
 * anti-dupe marking read and write through it.
 *
 * <p>This is deliberately distinct from the database-backed player-data store: PDC is per-holder, transient state
 * that lives with the entity, suited to a flag or a short-lived stamp; durable, server-authoritative data belongs
 * in the {@code PlayerDataStore} table instead.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.meta;
