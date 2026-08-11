package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmPowertool;

/**
 * The one piece of itemworld a consumer can usefully be told about: what a player's items have been bound to run.
 *
 * <p>The rest of the module is stateless. Repairing an item, opening a workstation, aliasing the weather: each is a
 * verb with nothing behind it to read, so there is nothing to publish. A powertool binding is different, because it
 * is state a player set and something a command-handling plugin has a real reason to know about before it decides
 * what a click meant.
 *
 * <p>Both reads reach into the player's live inventory, so both need the player online and answer empty when they
 * are not. The binding is stored on the item, so an item in a chest carries its binding and is simply not here.
 */
public interface UxmItemworldQuery {

    /** What the item in this player's main hand is bound to run, or empty for an empty or unbound hand. */
    CompletableFuture<Optional<UxmPowertool>> powertoolInHand(UUID playerId);

    /** Every bound item in this player's inventory, in slot order, empty when they carry none. */
    CompletableFuture<List<UxmPowertool>> powertools(UUID playerId);
}
