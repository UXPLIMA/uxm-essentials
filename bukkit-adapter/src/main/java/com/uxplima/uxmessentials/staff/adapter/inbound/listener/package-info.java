/**
 * The staff context's inbound listener: {@link com.uxplima.uxmessentials.staff.adapter.inbound.listener.StaffModeListener}
 * fires a gadget on right-click (resolving it by its PDC tag), cancels any drop/move of a gadget item so it
 * cannot leak out of the gadget hotbar, and restores the real loadout on a quit-in-mode so a logout never
 * strands a player in the gadget hotbar.
 */
@NullMarked
package com.uxplima.uxmessentials.staff.adapter.inbound.listener;

import org.jspecify.annotations.NullMarked;
