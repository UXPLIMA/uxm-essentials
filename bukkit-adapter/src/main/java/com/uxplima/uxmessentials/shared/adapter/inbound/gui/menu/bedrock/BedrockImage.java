package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import java.util.Objects;

/**
 * A Bedrock form button's icon in the engine's own vocabulary, free of any Cumulus type. A {@link Kind#PATH} value
 * is a Bedrock resource-pack texture path ({@code textures/items/diamond_sword}); a {@link Kind#URL} value is a web
 * image address the Bedrock client fetches itself (an {@code mc-heads.net} avatar for a player head). The
 * Cumulus-backed screen maps this onto a {@code FormImage} at the edge, so the engine never names the SDK.
 *
 * <p>A {@link Kind#URL} is a string handed to the client, not a server-side fetch: the Bedrock client loads the
 * image, so the server makes no HTTP call.
 *
 * @param kind whether {@code value} is a texture path or a web image URL; never {@code null}
 * @param value the texture path or URL; never {@code null}
 */
public record BedrockImage(Kind kind, String value) {

    /** Whether an image is a Bedrock texture path or a web image URL. */
    public enum Kind {
        PATH,
        URL
    }

    public BedrockImage {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
    }
}
