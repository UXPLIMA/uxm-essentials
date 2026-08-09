package com.uxplima.uxmessentials.api.query;

import java.util.Set;
import java.util.UUID;

/**
 * Who is hidden, and from whom.
 *
 * <p>This is the plugin's one vanish state: every surface that hides a player reads it, so a consumer that asks
 * here agrees with the tab list, the join messages and the staff tools rather than guessing alongside them.
 * Everything answers straight away, since the state is held in memory for the players who are online.
 *
 * <p>Vanish has levels. A hidden player is hidden <em>at</em> a level, and a viewer sees them only when the
 * viewer's own level reaches it, so "is this player vanished" and "can this player see them" are different
 * questions with different answers. A plugin deciding whether to show somebody in a list wants
 * {@link #canSee(UUID, UUID)}, not {@link #isVanished(UUID)}.
 */
public interface UxmVanishQuery {

    /** Whether this player is hidden right now. */
    boolean isVanished(UUID playerId);

    /** Every player hidden right now. Usually empty, and never large. */
    Set<UUID> vanished();

    /**
     * The level this player is hidden at, counting from one, or zero when they are not hidden. A higher level
     * hides them from more viewers.
     */
    int levelOf(UUID playerId);

    /**
     * Whether the viewer may see the target: true when the target is not hidden, when the two are the same
     * player, or when the viewer's level reaches the target's.
     */
    boolean canSee(UUID viewerId, UUID targetId);
}
