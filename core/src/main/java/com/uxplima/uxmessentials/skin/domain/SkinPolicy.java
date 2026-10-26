package com.uxplima.uxmessentials.skin.domain;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The rules an operator sets around what a player may wear: which skin names nobody may take, which hosts a url
 * may point at, and which pool a player who has chosen nothing is dressed from. Pure, so a refusal can be decided
 * without a network call or a server.
 *
 * @param blockedSkins names nobody may wear, matched without regard to case
 * @param restrictedSkins names only a player holding the skin's own node may wear; every other name is open
 * @param allowedUrlHosts the hosts a url may point at; empty allows any host
 * @param defaultPool the names an undressed player may be dressed from; empty leaves them as they are
 */
@NullMarked
public record SkinPolicy(
        List<String> blockedSkins,
        List<String> restrictedSkins,
        List<String> allowedUrlHosts,
        List<String> defaultPool) {

    /** The prefix every per-skin permission node is built on. */
    private static final String NAME_NODE = "uxmessentials.skin.name.";

    public SkinPolicy {
        blockedSkins = lowerCased(blockedSkins, "blockedSkins");
        restrictedSkins = lowerCased(restrictedSkins, "restrictedSkins");
        allowedUrlHosts = lowerCased(allowedUrlHosts, "allowedUrlHosts");
        defaultPool = List.copyOf(Objects.requireNonNull(defaultPool, "defaultPool"));
    }

    /** Whether {@code skinName} is one the operator forbade. */
    public boolean blocked(String skinName) {
        Objects.requireNonNull(skinName, "skinName");
        return blockedSkins.contains(skinName.toLowerCase(Locale.ROOT));
    }

    /** Whether {@code skinName} is one the operator reserved behind its node, matched without regard to case. */
    public boolean restricted(String skinName) {
        Objects.requireNonNull(skinName, "skinName");
        return restrictedSkins.contains(skinName.toLowerCase(Locale.ROOT));
    }

    /** Whether {@code url} points at a host the operator allows. A url that will not parse is never allowed. */
    public boolean urlAllowed(String url) {
        Objects.requireNonNull(url, "url");
        if (allowedUrlHosts.isEmpty()) {
            return true;
        }
        String host = hostOf(url);
        return host != null && allowedUrlHosts.contains(host);
    }

    /**
     * The permission node {@code skinName} sits behind once the operator {@linkplain #restricted restricted} it, so a
     * skin can be reserved for a rank. A skin nobody restricted is open to everyone, node or not.
     */
    public String permissionFor(String skinName) {
        Objects.requireNonNull(skinName, "skinName");
        return NAME_NODE + skinName.toLowerCase(Locale.ROOT);
    }

    /**
     * The pool entry {@code player} is dressed from when nothing else resolved. Chosen from the uuid, so a player
     * keeps the same face on every join rather than looking like a different person each time.
     */
    public Optional<String> fallbackFor(UUID player) {
        Objects.requireNonNull(player, "player");
        if (defaultPool.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(defaultPool.get(Math.floorMod(player.hashCode(), defaultPool.size())));
    }

    private static @Nullable String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static List<String> lowerCased(List<String> values, String field) {
        Objects.requireNonNull(values, field);
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }
}
