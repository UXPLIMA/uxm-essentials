package com.uxplima.uxmessentials.api.query;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmRegion;
import java.util.List;
import java.util.UUID;

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

    /** Regions whose bounds come within {@code radius} blocks of {@code where}, nearest first. */
    List<UxmRegion> within(UxmLocation where, int radius);

    /** The distance claims must keep from a guard region, from the guard config. */
    int claimDistance();

    /** Add action ids (and their parents) to the tree, for a plugin's own protections. */
    void registerActions(List<String> ids);
}
