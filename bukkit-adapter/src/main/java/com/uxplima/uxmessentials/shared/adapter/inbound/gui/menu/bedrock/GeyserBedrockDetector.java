package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The Geyser-backed {@link BedrockDetector}, for networks that run Geyser without Floodgate. Geyser alone still
 * knows which connections came from Bedrock, so those servers can have native forms too instead of falling back
 * to a chest menu for every Bedrock player.
 *
 * <p>Unlike its Floodgate sibling this is reached entirely by reflection: Floodgate is a {@code compileOnly}
 * dependency and can be named directly, Geyser is not one, so no {@code org.geysermc.geyser} type appears in
 * this file's signature and a server without Geyser loads none of its classes.
 *
 * <p>A lookup that fails answers "Java player", the same degrade the Floodgate detector makes: a menu open must
 * never fail because Bedrock detection hiccuped, and a chest menu is a working answer for a Bedrock player while
 * a thrown exception is not.
 */
final class GeyserBedrockDetector implements BedrockDetector {

    private static final String API_CLASS = "org.geysermc.geyser.api.GeyserApi";

    private @Nullable Method api;
    private @Nullable Method isBedrockPlayer;

    @Override
    public boolean isBedrock(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            Method entry = api;
            Method probe = isBedrockPlayer;
            if (entry == null || probe == null) {
                Class<?> apiType = Class.forName(API_CLASS);
                entry = apiType.getMethod("api");
                // Resolved on the interface rather than on the returned object: Geyser's implementation class is
                // an internal one, and a method handle taken from it would be unreachable from here.
                probe = apiType.getMethod("isBedrockPlayer", UUID.class);
                api = entry;
                isBedrockPlayer = probe;
            }
            Object geyser = entry.invoke(null);
            if (geyser == null) {
                return false;
            }
            return Boolean.TRUE.equals(probe.invoke(geyser, player));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notReady) {
            return false;
        }
    }

    @Override
    public String backend() {
        return "geyser";
    }
}
