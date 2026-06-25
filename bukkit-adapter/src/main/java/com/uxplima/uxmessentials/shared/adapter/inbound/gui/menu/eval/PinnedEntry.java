package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval;

/**
 * A list entry that asks to occupy a fixed content slot on every page, outside the scrolling flow; the engine
 * honours it only when the slot is one of the list's content slots.
 */
public interface PinnedEntry {

    int pinnedSlot();
}
