/**
 * The persistence adapter for the per-player switches every backend of one database shares ({@code /msgtoggle},
 * {@code /broadcasttoggle}, {@code /socialspy}): one row per player and switch they set. SQL is issued only through
 * the typed jOOQ DSL.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.settings;
