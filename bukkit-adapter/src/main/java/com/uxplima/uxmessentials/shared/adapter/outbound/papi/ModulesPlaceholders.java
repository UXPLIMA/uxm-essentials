package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

/**
 * Read seam for the {@code module_<id>} family: whether one feature module is switched on. Like the server
 * metrics this belongs to no context and is wired unconditionally, because the question it answers is most useful
 * exactly when the answer is no.
 *
 * <p>It is what lets a HUD line degrade honestly. A scoreboard that reads {@code %uxmessentials_homes_count%}
 * shows a dash when the homes module is off; reading {@code %uxmessentials_module_homes%} first tells the
 * operator's own template why, and lets them hide the line instead of printing a dash at every player.
 */
public interface ModulesPlaceholders {

    /** Whether the module with this id is registered and enabled; false for an id no registry knows. */
    boolean enabled(String moduleId);
}
