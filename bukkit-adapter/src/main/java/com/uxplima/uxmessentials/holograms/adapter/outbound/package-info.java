/**
 * The holograms context's outbound adapter: {@code HologramRenderer} realises the core {@code HologramView}
 * port over the uxmLib native-Display hologram API, mapping each domain hologram to one live multi-line
 * {@code TextDisplay}. Every spawn / re-render / despawn hops onto the owning region thread (Folia) through
 * the kernel {@code Scheduler} port; an unloaded world is skipped with a warning.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.holograms.adapter.outbound;
