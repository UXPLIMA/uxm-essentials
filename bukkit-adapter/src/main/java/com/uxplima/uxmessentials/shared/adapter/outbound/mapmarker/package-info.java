/**
 * The map-markers integration's outbound Bukkit adapters: the Dynmap and squaremap publishers that satisfy
 * the {@code MapMarkerPublisher} port, the warp/spawn/home sources a refresh reads, and the {@code
 * MapMarkerPublishers} discoverer that binds whichever supported map plugin is present (or the no-op
 * publisher when neither is). Each map-plugin SDK ({@code org.dynmap.*}, {@code xyz.jpenilla.squaremap.*})
 * is a {@code compileOnly} soft-depend reached only past a plugin-present guard, exactly like the economy
 * provider and PlaceholderAPI adapters, so the plugin enables fully with neither map plugin installed.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.mapmarker;
