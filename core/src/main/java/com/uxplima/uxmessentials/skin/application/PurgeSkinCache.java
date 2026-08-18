package com.uxplima.uxmessentials.skin.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import org.jspecify.annotations.NullMarked;

/**
 * Forgets the cached texture for one skin name, so the next lookup goes back to Mojang.
 *
 * <p>The cache is what keeps a busy server from being rate-limited, but a player who changed their skin at Mojang
 * keeps wearing the old one here until it expires. Staff drop that one name instead of waiting.
 */
@NullMarked
public final class PurgeSkinCache {

    private final SkinTextures textures;

    public PurgeSkinCache(SkinTextures textures) {
        this.textures = Objects.requireNonNull(textures, "textures");
    }

    /** Forget whatever is cached for {@code username}. */
    public void purge(String username) {
        Objects.requireNonNull(username, "username");
        textures.purge(username);
    }
}
