package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The live {@link MojangProfileSource}. An online name reads the player's own {@link PlayerProfile}, which costs no
 * network and picks up whatever a skin plugin put there; any other name goes to the shared {@link SkinTextures} fetch,
 * a blocking call the resolver only makes on the async scheduler.
 *
 * <p>The fetch asks Mojang directly rather than through {@code PlayerProfile.complete()}, which consults the session
 * service only on an online-mode server: completing a profile on a cracked server returns no textures at all, so every
 * tablist skin by name used to be blank there. Reading a live player's profile stays a Bukkit call because that is
 * where another plugin's skin would be.
 *
 * <p>Every read fails closed: an unknown name, an absent {@code textures} property, or a fetch failure returns empty so
 * the resolver falls back to the no-skin native path.
 */
@NullMarked
public final class BukkitMojangProfileSource implements MojangProfileSource {

    private static final String TEXTURES_PROPERTY = "textures";

    private final SkinTextures skins;

    public BukkitMojangProfileSource(SkinTextures skins) {
        this.skins = Objects.requireNonNull(skins, "skins");
    }

    @Override
    public Optional<TabSkin> onlineTexture(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online == null) {
            return Optional.empty();
        }
        return textureOf(online.getPlayerProfile());
    }

    @Override
    public Optional<TabSkin> fetchTexture(String name) {
        // Blocking Mojang round-trip; the resolver guarantees this runs on the async scheduler, never a tick thread.
        // The source swallows and logs every failure itself, so a rate-limit or an outage arrives here as an empty
        // result and the tablist keeps rendering on the native path.
        Optional<SkinTexture> fetched = skins.fetchNow(name);
        return fetched.map(texture -> new TabSkin(texture.value(), texture.signature()));
    }

    /** Pull the {@code textures} property off a profile and map it to a {@link TabSkin}; empty when none is present. */
    private static Optional<TabSkin> textureOf(PlayerProfile profile) {
        for (ProfileProperty property : profile.getProperties()) {
            if (TEXTURES_PROPERTY.equals(property.getName())) {
                @Nullable String signature = property.getSignature();
                return Optional.of(new TabSkin(property.getValue(), signature));
            }
        }
        return Optional.empty();
    }
}
