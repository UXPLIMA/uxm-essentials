/**
 * The holograms context's inbound management-GUI adapter: {@code /hologram} with no arguments opens
 * {@link com.uxplima.uxmessentials.holograms.adapter.inbound.gui.HologramListView}, a config-driven list of
 * every stored hologram, and clicking one opens
 * {@link com.uxplima.uxmessentials.holograms.adapter.inbound.gui.HologramEditorView}, a property grid exposing
 * each hologram property. Both are thin consumers of the shared SP0 GUI framework
 * ({@code shared.adapter.inbound.gui}); every edit flows through the holograms application use cases, so the
 * GUI adds no domain logic and stays in lockstep with the equivalent {@code /hologram} subcommands.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.holograms.adapter.inbound.gui;
