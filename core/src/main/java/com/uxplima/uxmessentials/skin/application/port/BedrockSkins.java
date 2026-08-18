package com.uxplima.uxmessentials.skin.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.jspecify.annotations.NullMarked;

/**
 * The skin a Bedrock player wears, read from whatever tells the server about Bedrock accounts (Floodgate for the
 * xuid, the Geyser skin service for the signed texture).
 *
 * <p>A Java player is simply not a Bedrock one: {@link #byPlayer(UUID)} answers empty for them without a lookup.
 * On a server with no Floodgate at all, {@link #available()} is false and the whole branch is skipped.
 */
@NullMarked
public interface BedrockSkins {

    /** The Bedrock skin {@code player} wears, or empty when they are not a Bedrock player or the lookup failed. */
    Optional<SkinTexture> byPlayer(UUID player);

    /** Whether Bedrock lookups can happen at all here, that is whether Floodgate is installed. */
    boolean available();

    /** The lookup for a server with no Floodgate: nobody is a Bedrock player. */
    static BedrockSkins none() {
        return new BedrockSkins() {
            @Override
            public Optional<SkinTexture> byPlayer(UUID player) {
                return Optional.empty();
            }

            @Override
            public boolean available() {
                return false;
            }
        };
    }
}
