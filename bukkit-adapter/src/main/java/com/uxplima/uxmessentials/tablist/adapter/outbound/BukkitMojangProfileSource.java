package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The live {@link MojangProfileSource}: reads textures from Bukkit player profiles. An online name reads the player's
 * live {@link PlayerProfile} (no network); an offline name builds a profile with {@code Bukkit.createProfile(name)} and
 * {@link PlayerProfile#complete() completes} it against Mojang — a blocking call the resolver only makes on the async
 * scheduler. Every read fails closed: an unknown name, an absent {@code textures} property, or a fetch failure returns
 * empty so the resolver falls back to the no-skin native path.
 *
 * <p>A fetch failure (a misspelled {@code player:<name>}, a Mojang rate-limit, a network outage) is logged at debug
 * before the empty fall-back so the failure leaves an operator signal without spamming the default log — a bad name is
 * not worth a warning, and the tablist still renders fine on the native path.
 */
@NullMarked
public final class BukkitMojangProfileSource implements MojangProfileSource {

    private static final String TEXTURES_PROPERTY = "textures";

    private final Logger log;

    public BukkitMojangProfileSource(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
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
        try {
            PlayerProfile profile = Bukkit.createProfile(name);
            // Blocking Mojang round-trip; the resolver guarantees this runs on the async scheduler, never a tick
            // thread.
            profile.complete(true);
            return textureOf(profile);
        } catch (RuntimeException failure) {
            // A network failure or rate-limit must not blank the tablist: fall back to no skin (the native path).
            // Debug,
            // not warn, so a single bad name does not spam the log while the operator still gets a signal to grep.
            log.debug("tablist skin fetch failed for {}: {}", name, failure);
            return Optional.empty();
        }
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
