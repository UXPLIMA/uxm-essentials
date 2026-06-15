package com.uxplima.uxmessentials.npc.domain;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A fake player's skin as the two raw strings the game protocol carries: the base64 value of the Mojang
 * profile's "textures" property and its (optional) Yggdrasil signature. The domain stores them verbatim — how
 * they reach a client (a player-info entry on a spawn packet) is an adapter concern — so the aggregate never
 * imports a Bukkit profile type. A {@code null} signature is an unsigned skin (still rendered, though some
 * clients reject an unsigned skin from another player's account); a blank {@code texture} is rejected so a skin
 * object always carries something to render.
 *
 * @param texture the base64-encoded skin-property value
 * @param signature the Yggdrasil signature for {@code texture}, or {@code null} for an unsigned skin
 */
public record NpcSkin(String texture, @Nullable String signature) {

    public NpcSkin {
        Objects.requireNonNull(texture, "texture");
        if (texture.isBlank()) {
            throw new IllegalArgumentException("npc skin texture must not be blank");
        }
    }

    /** A skin carrying only a texture value, with no signature (an unsigned skin). */
    public static NpcSkin unsigned(String texture) {
        return new NpcSkin(texture, null);
    }
}
