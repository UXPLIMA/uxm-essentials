/**
 * The villagers context's domain: the pure decisions behind villager trade management. Phase 1 holds
 * {@link com.uxplima.uxmessentials.villagers.domain.RestockPolicy}, the timer rule that answers whether a
 * villager last restocked at some instant is due to restock again given the configured interval. The value
 * object owns only its own structural invariant (a positive interval); the villager, the merchant recipes,
 * and the persistent last-restock stamp are all adapter concerns. Pure Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.villagers.domain;

import org.jspecify.annotations.NullMarked;
