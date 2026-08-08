package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.npc.application.port.SkinService;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import org.jspecify.annotations.NullMarked;

/**
 * Fronts the {@code SkinService} port over the two remote skin sources, routing each method to the leg that owns
 * it: {@link #fetchByName} to the shared {@link SkinTextures} (a username to signed-texture lookup, the same one
 * the tablist uses) and
 * {@link #fetchFromUrl} to the {@link MineSkinService} (an image URL → generated signed texture). Each leg keeps
 * its own HTTP seam, timeout and cache; this is a thin router so neither leg has to carry a method it does not
 * implement, and the command surface sees one fail-soft port.
 */
@NullMarked
public final class CompositeSkinService implements SkinService {

    private final SkinTextures byName;
    private final MineSkinService fromUrl;

    public CompositeSkinService(SkinTextures byName, MineSkinService fromUrl) {
        this.byName = Objects.requireNonNull(byName, "byName");
        this.fromUrl = Objects.requireNonNull(fromUrl, "fromUrl");
    }

    @Override
    public CompletableFuture<Optional<NpcSkin>> fetchByName(String username) {
        return byName.byName(Objects.requireNonNull(username, "username"))
                .thenApply(texture -> texture.map(found -> new NpcSkin(found.value(), found.signature())));
    }

    @Override
    public CompletableFuture<Optional<NpcSkin>> fetchFromUrl(String imageUrl) {
        return fromUrl.fetchFromUrl(Objects.requireNonNull(imageUrl, "imageUrl"));
    }
}
