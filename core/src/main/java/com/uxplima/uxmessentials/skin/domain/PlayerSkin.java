package com.uxplima.uxmessentials.skin.domain;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.jspecify.annotations.NullMarked;

/**
 * The skin a player wears, as it is stored: who owns it, where it came from, the texture the client is dressed
 * with, the model that texture was cut for, and when it was applied.
 *
 * <p>Storing the resolved texture beside its source is what keeps a login free of network calls: for a player who
 * has chosen a skin, the row is the whole answer.
 *
 * @param owner the player wearing it
 * @param source where the texture was resolved from
 * @param texture the signed (or unsigned) profile texture
 * @param model the player model the texture was cut for
 * @param appliedAt when the choice was made
 */
@NullMarked
public record PlayerSkin(PlayerRef owner, SkinSource source, SkinTexture texture, SkinModel model, Instant appliedAt) {

    public PlayerSkin {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(appliedAt, "appliedAt");
    }
}
