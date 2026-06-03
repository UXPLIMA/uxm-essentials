/**
 * The holograms context's bukkit-side wiring: {@code HologramsWiring} constructs the use cases over the
 * kernel ports, the cached jOOQ repository, and the uxmLib-backed renderer, and {@code HologramServices}
 * bundles them for the single {@code /hologram} command. The renderer's live display entities are despawned
 * on module stop so a reload re-spawns cleanly.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.holograms.adapter;
