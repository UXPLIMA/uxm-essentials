/**
 * The holograms context's use cases and outbound ports. The use cases orchestrate the {@code Hologram}
 * aggregate through the {@code HologramRepository} port, drive the in-world rendering through the
 * {@code HologramView} port (spawn / re-render / move / despawn the native display, realised on the right
 * region thread in the adapter), and render feedback through the {@code Messages}/{@code MessageSink} pair.
 * Holograms are an operator surface, so the single {@code /hologram} command is gated as a whole and the
 * list is unfiltered. The {@code HologramsModule} declares the context's command and enable gate. No Bukkit,
 * Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.holograms.application;
