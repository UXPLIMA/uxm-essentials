/**
 * The playerstate context's GUI: {@link com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.InvseeView}
 * backs {@code /invsee} with a managed 54-slot menu that mirrors a target's full inventory — main slots, armour,
 * and offhand — into a private copy the viewer edits, then reconciles that copy back onto the target on close.
 * The viewer never holds a handle to the target's live {@code PlayerInventory}, which is what closes the classic
 * raw-inventory dupe window. {@link com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.InvseeListener}
 * routes the click/drag/close events for these menus, identified by their
 * {@link com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.InvseeHolder}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;
