package com.uxplima.uxmessentials.worlds.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.domain.AccessDecision;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import org.jspecify.annotations.NullMarked;

/**
 * The pure entry-gate decision for a world, shared by the {@code /world} enter command use case and the
 * cross-world teleport listener so both apply byte-identical rules and the permissions guard can find the
 * literal nodes in one place.
 *
 * <p>A player carrying {@link #BYPASS_NODE} always enters. Otherwise a world flagged
 * {@link WorldProperties#ACCESS_RESTRICTED} requires the player to hold that world's {@link #enterNode}, and a
 * world with a positive {@link WorldProperties#PLAYER_LIMIT} refuses once the live count reaches it; a zero
 * limit means unlimited.
 */
@NullMarked
public final class WorldAccessPolicy {

    /** The node that lets a player bypass every world entry restriction. */
    public static final String BYPASS_NODE = "uxmessentials.world.access.bypass";

    private final Permissions permissions;
    private final WorldEngine engine;

    public WorldAccessPolicy(Permissions permissions, WorldEngine engine) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    /** The per-world node a restricted world requires for entry. */
    public static String enterNode(WorldName name) {
        return "uxmessentials.world." + name.value() + ".enter";
    }

    /** Evaluate whether {@code who} may enter {@code world}, returning the reason when denied. */
    public AccessDecision decide(PlayerRef who, ManagedWorld world) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(world, "world");
        if (permissions.has(who, BYPASS_NODE)) {
            return AccessDecision.ALLOWED;
        }
        if (world.settings().get(WorldProperties.ACCESS_RESTRICTED) && !permissions.has(who, enterNode(world.name()))) {
            return AccessDecision.DENIED_PERMISSION;
        }
        int limit = world.settings().get(WorldProperties.PLAYER_LIMIT);
        if (limit > 0 && engine.playerCount(world.name()) >= limit) {
            return AccessDecision.DENIED_FULL;
        }
        return AccessDecision.ALLOWED;
    }
}
