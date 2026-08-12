package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code itemworld_*} placeholders: the two personal switches the item
 * verbs carry ({@code /powertool toggle} and {@code /unlimited}) and whatever the held item is bound to run.
 * Wired during itemworld wiring; with the module disabled the seam is absent and every key degrades to the dash.
 */
public interface ItemworldPlaceholders {

    /** The commands bound to the item {@code who} holds, in the order they run; empty when nothing is bound. */
    List<String> powertool(PlayerRef who);

    /** Whether {@code who} currently lets their powertool bindings fire ({@code /powertooltoggle}). */
    boolean powertoolEnabled(PlayerRef who);

    /** Whether {@code who} is placing blocks without consuming them ({@code /unlimited}). */
    boolean unlimitedPlacement(PlayerRef who);
}
