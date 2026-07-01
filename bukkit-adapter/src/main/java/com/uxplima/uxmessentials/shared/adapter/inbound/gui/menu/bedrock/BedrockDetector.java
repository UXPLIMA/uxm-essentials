package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Server;

/**
 * Tells whether a player is a Bedrock (Floodgate) player, so the later hybrid renderer can send them a native
 * Bedrock form instead of a chest menu.
 *
 * <p>The Java-only default is {@link #NONE}: it answers {@code false} for everyone and carries no Floodgate
 * reference, so a server without Floodgate never touches the SDK. When Floodgate is installed the
 * {@link #forServer(Server)} factory returns the Floodgate-backed detector instead — that concrete class is the
 * only place the {@code org.geysermc.floodgate} SDK is named, and the factory constructs it strictly behind the
 * {@code isPluginEnabled("floodgate")} guard, so it (and the SDK it references) never loads on a Java-only
 * server.
 */
public interface BedrockDetector {

    /** The Java-only default: everyone is a Java player, and no Floodgate class is referenced. */
    BedrockDetector NONE = player -> false;

    /**
     * Whether {@code player} connected through Floodgate as a Bedrock player.
     *
     * @param player the player's unique id; never {@code null}
     * @return {@code true} only when a Floodgate-backed detector confirms it; always {@code false} for {@link #NONE}
     */
    boolean isBedrock(UUID player);

    /**
     * Selects the detector for this server: the Floodgate-backed one when the {@code floodgate} plugin is enabled,
     * otherwise {@link #NONE}. The Floodgate-backed detector is named only inside the enabled branch, so on a
     * server without Floodgate its class — and the {@code org.geysermc.floodgate} SDK it references — is never
     * loaded.
     *
     * @param server the running server, used only to probe plugin presence; never {@code null}
     * @return a Floodgate-backed detector when Floodgate is enabled, else {@link #NONE}
     */
    static BedrockDetector forServer(Server server) {
        Objects.requireNonNull(server, "server");
        if (server.getPluginManager().isPluginEnabled("floodgate")) {
            return new FloodgateBedrockDetector();
        }
        return NONE;
    }
}
