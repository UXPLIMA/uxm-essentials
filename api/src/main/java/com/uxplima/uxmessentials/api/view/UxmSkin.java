package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;

/**
 * The skin a player chose, as the server stored it.
 *
 * <p>The texture itself is not here. It is a signed blob meaningful only to the client, and a consumer that wants
 * to draw a face wants the source instead: a name to look up, a url to fetch, the file the operator dropped on the
 * server, or the Bedrock account the skin came from.
 *
 * @param sourceType where the skin came from: {@code BY_NAME}, {@code BY_URL}, {@code BY_FILE}, {@code BEDROCK}
 *     or {@code FALLBACK}, the same spelling the stored row carries
 * @param sourceValue what that source names: the account, the link, the file or the xuid
 * @param slim whether the image was cut for the three-pixel arm
 * @param appliedAt when the player last took it
 */
public record UxmSkin(String sourceType, String sourceValue, boolean slim, Instant appliedAt) {

    public UxmSkin {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceValue, "sourceValue");
        Objects.requireNonNull(appliedAt, "appliedAt");
    }
}
