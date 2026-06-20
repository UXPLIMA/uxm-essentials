/**
 * The npc context's management GUI: a config-driven list of every stored NPC ({@link
 * com.uxplima.uxmessentials.npc.adapter.inbound.gui.NpcListView}) opening a per-NPC property editor ({@link
 * com.uxplima.uxmessentials.npc.adapter.inbound.gui.NpcEditorView}). Both consume the shared SP0 framework
 * ({@code shared.adapter.inbound.gui}) and write every change through the existing npc application use cases —
 * the GUI adds no domain logic, it is a thin inbound adapter over the same use cases the {@code /npc}
 * subcommands call. Geometry and materials come from {@code modules/npc/gui/*.conf}; all text from the
 * {@code NpcMessageKey} catalog.
 */
@NullMarked
package com.uxplima.uxmessentials.npc.adapter.inbound.gui;

import org.jspecify.annotations.NullMarked;
