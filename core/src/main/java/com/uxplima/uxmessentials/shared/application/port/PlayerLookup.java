package com.uxplima.uxmessentials.shared.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port that resolves player identities without exposing a Bukkit {@code Player}.
 *
 * <p>The adapter looks players up by name or UUID and maps the result to a {@link PlayerRef}.
 * Resolution by name returns the online player when one matches; offline-name resolution that hits a
 * profile cache or the mojang lookup is the adapter's concern and may be empty. Application code never
 * iterates {@code Bukkit.getOnlinePlayers()}: it asks this port.
 *
 * <p>On an offline-mode server {@code Steve} and {@code steve} are two accounts. A name spelled exactly as one of
 * them resolves to it; a name that matches several only in case resolves to none of them, never to a guess. The
 * {@code find} methods then answer empty, and the {@code match} methods say which spellings exist so a command can
 * tell its sender to type one exactly.
 */
public interface PlayerLookup {

    /** The online player this name names, if one is connected; empty when it could mean several accounts. */
    Optional<PlayerRef> findOnlineByName(String name);

    /**
     * Like {@link #findOnlineByName}, but says when the name could mean several accounts. The default knows no
     * case variants, so a test fake stays a plain single-answer lookup.
     */
    default NameMatch matchOnlineByName(String name) {
        return NameMatch.of(findOnlineByName(name));
    }

    /**
     * The player with this exact name, online first and otherwise a profile that has played before. Unlike
     * {@link #findOnlineByName}, this resolves an offline owner so a public player-warp stays reachable while
     * its owner is offline; a name the server has never seen resolves to empty. The default delegates to
     * {@link #findOnlineByName} so a test fake stays online-only unless it overrides this.
     */
    default Optional<PlayerRef> findByName(String name) {
        return findOnlineByName(name);
    }

    /** Like {@link #findByName}, but says when the name could mean several accounts. */
    default NameMatch matchByName(String name) {
        return NameMatch.of(findByName(name));
    }

    /** The player with this UUID, online or resolvable from the profile cache. */
    Optional<PlayerRef> findByUuid(UUID uuid);

    /** True when the player with this UUID is currently connected. */
    boolean isOnline(UUID uuid);
}
