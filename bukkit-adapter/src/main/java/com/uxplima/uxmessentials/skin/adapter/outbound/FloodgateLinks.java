package com.uxplima.uxmessentials.skin.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Server;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.jspecify.annotations.NullMarked;

/**
 * The Java account a Bedrock player linked through Floodgate, so a Bedrock skin that cannot be read falls back to that
 * account's own skin rather than the pool.
 *
 * <p>Floodgate is a soft dependency: whether it is enabled is asked on each lookup (it enables after this plugin), and
 * only {@link Api} names its classes, so a server without it never loads them.
 */
@NullMarked
public final class FloodgateLinks {

    private static final String FLOODGATE = "floodgate";

    private FloodgateLinks() {}

    /** The linked Java account of a player by uuid, or empty without Floodgate or a link. */
    public static Function<UUID, Optional<PlayerRef>> of(Server server) {
        Objects.requireNonNull(server, "server");
        return player -> server.getPluginManager().isPluginEnabled(FLOODGATE) ? Api.linked(player) : Optional.empty();
    }

    /** The one place Floodgate's classes are named, loaded only once Floodgate is known to be there. */
    private static final class Api {

        private Api() {}

        static Optional<PlayerRef> linked(UUID player) {
            FloodgatePlayer bedrock = FloodgateApi.getInstance().getPlayer(player);
            if (bedrock == null || !bedrock.isLinked()) {
                return Optional.empty();
            }
            return Optional.of(new PlayerRef(bedrock.getJavaUniqueId(), bedrock.getJavaUsername()));
        }
    }
}
