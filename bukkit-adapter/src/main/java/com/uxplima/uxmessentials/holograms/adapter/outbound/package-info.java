/**
 * The holograms context's outbound adapter: {@code PacketHologramView} realises the core {@code HologramView} port
 * by drawing each stored hologram for every viewer with packets, through the uxmLib packet hologram. Nothing is
 * spawned in the world; an unloaded world is skipped with a warning.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.holograms.adapter.outbound;
