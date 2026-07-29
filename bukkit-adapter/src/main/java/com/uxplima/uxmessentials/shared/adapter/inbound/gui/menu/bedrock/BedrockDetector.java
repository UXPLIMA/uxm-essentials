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
 * {@link #forServer(Server)} factory returns the Floodgate-backed detector instead: that concrete class is the
 * only place the {@code org.geysermc.floodgate} SDK is named, and the factory constructs it strictly behind the
 * {@code isPluginEnabled("floodgate")} guard, so it (and the SDK it references) never loads on a Java-only
 * server.
 *
 * <p>Geyser without Floodgate is the second supported shape. Those servers still carry Bedrock players, and
 * Geyser can name them, so the factory falls through to a Geyser-backed detector when Floodgate is absent.
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
     * Which plugin answers this detector's questions, for the one line the bootstrap logs on enable. Operators
     * read it to tell the two working shapes apart, so it names the source rather than the class.
     *
     * @return {@code "none"}, {@code "floodgate"} or {@code "geyser"}
     */
    default String backend() {
        return "none";
    }

    /**
     * Selects the detector for this server: the Floodgate-backed one when the {@code floodgate} plugin is enabled,
     * the Geyser-backed one when only {@code Geyser-Spigot} is, otherwise {@link #NONE}. Each backed detector is
     * named only inside its own branch, so on a server with neither plugin their classes (and the
     * {@code org.geysermc} SDKs they reference) are never loaded.
     *
     * <p>Floodgate wins when both are installed. It is the richer source, it is a compile-time dependency rather
     * than a reflective one, and it is the path with a Bedrock link account behind it.
     *
     * @param server the running server, used only to probe plugin presence; never {@code null}
     * @return a Floodgate-backed detector when Floodgate is enabled, a Geyser-backed one when only Geyser is,
     *     else {@link #NONE}
     */
    static BedrockDetector forServer(Server server) {
        Objects.requireNonNull(server, "server");
        if (server.getPluginManager().isPluginEnabled("floodgate")) {
            return new FloodgateBedrockDetector();
        }
        if (server.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
            return new GeyserBedrockDetector();
        }
        return NONE;
    }
}
