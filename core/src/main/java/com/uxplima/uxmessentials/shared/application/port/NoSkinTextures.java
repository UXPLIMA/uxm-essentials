package com.uxplima.uxmessentials.shared.application.port;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import org.jspecify.annotations.NullMarked;

/** The {@link SkinTextures} that resolves nothing, so a caller with no source wired takes its no-skin path. */
@NullMarked
final class NoSkinTextures implements SkinTextures {

    static final NoSkinTextures INSTANCE = new NoSkinTextures();

    private NoSkinTextures() {}

    @Override
    public CompletableFuture<Optional<SkinTexture>> byName(String username) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public Optional<SkinTexture> fetchNow(String username) {
        return Optional.empty();
    }
}
