package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.permission.PlayerstatePermissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Whether a viewer may open a storage window on the player they picked from the {@code /uxmess gui} hub. The hub tile
 * is gated on the verb's use node, as the bare command is, but a picker offers every player; so a pick of anyone other
 * than the viewer takes the verb's own others node or the cross-cutting playerstate one, the same rule a named target
 * meets on {@code /invsee} and {@code /endersee}.
 *
 * @param permissions the permission port the viewer's nodes are read through
 * @param othersNode the verb's own node for targeting another player
 */
@NullMarked
public record OthersGate(Permissions permissions, String othersNode) {

    public OthersGate {
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(othersNode, "othersNode");
    }

    /** True when {@code viewer} may open {@code target}'s storage. */
    public boolean allows(PlayerRef viewer, PlayerRef target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        return viewer.uuid().equals(target.uuid())
                || permissions.has(viewer, othersNode)
                || permissions.has(viewer, PlayerstatePermissions.PLAYERSTATE_OTHERS);
    }
}
