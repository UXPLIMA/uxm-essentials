package org.geysermc.geyser.api;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

/**
 * A stand-in for Geyser's API on the test class path, answering the two questions the library's Geyser-only detector
 * asks by name: who is a Bedrock player, and what their connection reports. Paired with a mock plugin named
 * {@code Geyser-Spigot}, it is a server running Geyser without Floodgate, the shape where no form screen exists and
 * the text-input seam falls back to the operator's mode.
 */
public final class GeyserApi {

    private static final GeyserApi INSTANCE = new GeyserApi();

    private final Set<UUID> bedrock = ConcurrentHashMap.newKeySet();

    private GeyserApi() {}

    public static GeyserApi api() {
        return INSTANCE;
    }

    /** Make {@code player} a Bedrock player connected through Geyser. */
    public static void connect(UUID player) {
        INSTANCE.bedrock.add(player);
    }

    /** Forget every Bedrock player, so the next scenario starts with none. */
    public static void reset() {
        INSTANCE.bedrock.clear();
    }

    public boolean isBedrockPlayer(UUID player) {
        return bedrock.contains(player);
    }

    /** No connection details: the detector reads an empty client for everyone. */
    public @Nullable Object connectionByUuid(UUID player) {
        return null;
    }
}
