package com.uxplima.uxmessentials.shared.adapter.outbound.skin;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.jspecify.annotations.NullMarked;

/**
 * What a skin service hands back: the signed texture plus the body model it was cut for.
 *
 * <p>Deliberately context-free, so the one client serves everybody who generates a skin: the npc context maps it
 * to its own {@code NpcSkin}, the skin context to a {@code PlayerSkin}. The model matters because a texture drawn
 * for the three-pixel arm wears with a seam on the four-pixel one.
 *
 * @param texture the signed profile texture
 * @param slim whether it was cut for the slim (Alex) model rather than the classic (Steve) one
 */
@NullMarked
public record SignedSkin(SkinTexture texture, boolean slim) {

    public SignedSkin {
        Objects.requireNonNull(texture, "texture");
    }

    /** The texture's base64 value. */
    public String value() {
        return texture.value();
    }
}
