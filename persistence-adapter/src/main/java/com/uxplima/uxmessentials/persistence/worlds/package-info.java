/**
 * The worlds context's outbound persistence adapter: the jOOQ {@code WorldRepository} over the generated DSL,
 * its in-memory read-cache decorator, and the claim of world rows no backend has recorded yet. Every world fact
 * is a first-class column in the {@code world} table (the backend that runs it, name, optional Bukkit uid,
 * environment, generation preset, optional seed, optional generator, optional dimension, the
 * structure/auto-load/adopted flags, creation time and creator), keyed by backend and name. There is no opaque
 * JSON blob, so a {@code ManagedWorld} is rebuilt from queryable rows. Boolean facets are stored as INT 0/1
 * because the network backends share this DSL. SQL is issued only through the typed jOOQ DSL, never string
 * concatenation.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.worlds;
