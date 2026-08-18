package com.uxplima.uxmessentials.skin.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import org.jspecify.annotations.NullMarked;

/**
 * Puts a resolved skin on a player who is already in the world.
 *
 * <p>The adapter replaces the texture on the player's profile and lets the server re-send them to everyone who can
 * see them, so no packet of ours is involved and there is no inventory, position or gamemode state to restore. A
 * player who has since logged out is silently left alone.
 */
@NullMarked
public interface SkinView {

    /** Dress {@code who} in {@code texture}. A no-op when they are offline. */
    void apply(PlayerRef who, SkinTexture texture, SkinModel model);
}
