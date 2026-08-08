package com.uxplima.uxmessentials.shared.domain;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A skin as the two raw strings the game protocol carries: the base64 value of a Mojang profile's
 * {@code textures} property and its (optional) Yggdrasil signature. Deliberately neutral, so the one fetch can
 * serve every context that dresses something in a player skin: an NPC maps it to its own {@code NpcSkin}, the
 * tablist maps it to uxmLib's {@code TabSkin}.
 *
 * @param value the base64-encoded skin-property value
 * @param signature the signature for {@code value}, or {@code null} for an unsigned texture
 */
@NullMarked
public record SkinTexture(String value, @Nullable String signature) {

    public SkinTexture {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("skin texture value must not be blank");
        }
    }
}
