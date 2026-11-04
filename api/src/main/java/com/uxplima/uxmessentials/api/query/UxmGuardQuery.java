package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmRegion;

/**
 * The guard module's protection, for other plugins: may a player do an action at a place, which regions lie near a
 * place, and room in the action tree for a plugin's own actions.
 *
 * <p>Checks read an immutable snapshot and are safe from any thread. An action is a dotted id from the guard's
 * action tree, such as {@code break}, {@code use.container} or {@code damage.player}; a plugin's own branch is
 * added with {@link #registerActions(List)} and can then be named in rules like any built-in action.
 */
public interface UxmGuardQuery {

    /**
     * Whether {@code player} may do {@code action} at {@code where}. A player who is not online is checked as a
     * player with no permissions. An unknown action or an unloaded world answers true: nothing protects it.
     */
    boolean allowed(UUID player, String action, UxmLocation where);

    /** Whether nature may do a {@code world.*} action at {@code where}. */
    boolean allowedWorld(String action, UxmLocation where);

    /**
     * Regions whose bounds come within {@code radius} blocks of {@code where} on the horizontal plane, at any
     * height, nearest first. A square, not a circle: what a chunk claim needs to keep its distance.
     */
    List<UxmRegion> within(UxmLocation where, int radius);

    /** The distance claims must keep from a guard region, from the guard config. */
    int claimDistance();

    /**
     * The value of a region behaviour (such as {@code fly}, {@code god} or {@code game-mode}) for an online player
     * where they stand now, or empty when no region there sets it or the player is offline. Tristate behaviours
     * answer {@code allow}, {@code deny} or {@code keep}.
     */
    Optional<String> behaviour(UUID player, String key);

    /** Add action ids (and their parents) to the tree, for a plugin's own protections. */
    void registerActions(List<String> ids);
}
