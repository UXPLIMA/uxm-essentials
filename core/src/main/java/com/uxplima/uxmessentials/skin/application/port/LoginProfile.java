package com.uxplima.uxmessentials.skin.application.port;

import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.jspecify.annotations.NullMarked;

/**
 * The profile of a player who is still connecting, narrow enough that the application layer can dress them
 * without knowing what a Bukkit profile is.
 *
 * <p>Dressing a login is the one place a skin costs nothing: the player has no entity yet, so the texture is
 * simply part of who arrives. There is no respawn, no re-send and nothing to flicker.
 */
@NullMarked
public interface LoginProfile {

    /** Put {@code texture} on this profile, replacing whatever it carried. */
    void dress(SkinTexture texture);
}
