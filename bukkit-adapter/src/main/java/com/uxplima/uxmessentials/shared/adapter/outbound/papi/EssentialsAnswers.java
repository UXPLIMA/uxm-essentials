package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.hook.placeholder.PlaceholderRegistry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What one of our keys answers, for whichever expansion PlaceholderAPI asked: the shared {@code uxm} one, which hands
 * over what follows {@code uxm_essentials_}, or the older {@code uxmessentials} one. A key builds a {@link PlayerRef}
 * from the requesting player and goes to the {@link PlaceholderResolver}; a value goes through
 * {@link MarkupPass#shown}, so inside one of our own lines it is the words it holds.
 *
 * <p>A key we do not have, a request with no player, or a relational request without both players answers
 * {@code null}, which PlaceholderAPI leaves in the text as written.
 */
@NullMarked
final class EssentialsAnswers {

    private final PlaceholderResolver resolver;

    EssentialsAnswers(PlaceholderResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * The registry the shared {@code uxm} expansion asks with what follows {@code uxm_essentials_}: every key is ours,
     * so it is the fallback rather than a prefix, which keeps an unknown key's {@code null} as it is.
     */
    PlaceholderRegistry registry() {
        return new PlaceholderRegistry().fallback(this::answer).relational(this::relational);
    }

    /** {@code key} for {@code player}, or {@code null} to leave the placeholder as written. */
    @Nullable String answer(@Nullable OfflinePlayer player, String key) {
        Objects.requireNonNull(key, "key");
        if (player == null || key.isEmpty()) {
            return null;
        }
        return resolver.resolve(refOf(player), isOnline(player.getUniqueId()), key)
                .map(value -> MarkupPass.shown(key, value))
                .orElse(null);
    }

    /** {@code key} for {@code viewer} reading about {@code target}, or {@code null} to leave it as written. */
    @Nullable String relational(@Nullable Player viewer, @Nullable Player target, String key) {
        Objects.requireNonNull(key, "key");
        if (viewer == null || target == null || key.isEmpty()) {
            return null;
        }
        return resolver.resolveRelational(refOf(viewer), refOf(target), key)
                .map(value -> MarkupPass.shown(key, value))
                .orElse(null);
    }

    private static PlayerRef refOf(OfflinePlayer player) {
        String name = player.getName();
        return new PlayerRef(
                player.getUniqueId(), name != null ? name : player.getUniqueId().toString());
    }

    private static boolean isOnline(UUID uuid) {
        return Optional.ofNullable(Bukkit.getPlayer(uuid)).isPresent();
    }
}
