package com.uxplima.uxmessentials.shared.application.port;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PresenceTraits;

/**
 * Which backend each online player is on, across the whole network. A context that names a player (a sanction, a
 * private message, a teleport request) asks this before it falls back to offline data, so a player on another
 * backend is found as online there rather than as an offline account.
 *
 * <p>A port because it is the test seam for those contexts: they read it through a fake, while the one
 * implementation keeps itself current from local joins and quits and the presence frames of the cross-server bus.
 * With the network off it knows the local players only, and {@link #localServer()} is the configured server name.
 *
 * <p>Every method is a memory read, safe on any thread including a tick thread. The view is eventually consistent:
 * a player who just switched backends may show on the old one until its quit frame or next heartbeat arrives.
 */
public interface NetworkPresence {

    /**
     * How long after a player left another backend their arrival here counts as a move between servers rather than a
     * join, where nothing configures it otherwise.
     */
    Duration SWITCH_WINDOW = Duration.ofSeconds(3);

    /** The {@code server-name} of this backend. */
    String localServer();

    /**
     * Where the player with this account is online, or empty when they are on no backend this server knows. While a
     * player switches backends the one this server saw them arrive on last answers; two arrivals seen in the same
     * millisecond are ordered by server name, so every lookup answers alike.
     */
    Optional<OnlinePlayer> find(UUID player);

    /**
     * Where the online player with this exact name is, ignoring case. A player on this backend wins over one with
     * the same name elsewhere, and an exact-case match wins over a match that differs in case. When only case
     * variants are online and they are several accounts, none is a match; with offline-mode logins only the spelling
     * typed is a match at all.
     */
    Optional<OnlinePlayer> findByName(String name);

    /**
     * Like {@link #findByName}, but says when the name could mean several accounts online, so the sender is told the
     * spellings rather than that nobody is online. The default knows no case variants.
     */
    default NameMatch matchByName(String name) {
        return NameMatch.of(findByName(name).map(OnlinePlayer::player));
    }

    /** The players online on {@code server}, ordered by name. */
    List<OnlinePlayer> onServer(String server);

    /** Every player online on any backend this server knows, ordered by server and then by name. */
    List<OnlinePlayer> online();

    /**
     * The traits announced for {@code player} on the backend {@link #find} places them on: whether they are hidden
     * and whether they are staff. Empty for a player online nowhere this server knows; the default knows no traits.
     */
    default Optional<PresenceTraits> traits(UUID player) {
        return Optional.empty();
    }

    /**
     * The other backend {@code player} is online on, or left within {@code within}, as far as this server heard: the
     * backend a player who is joining here comes from when they move across the network. Empty for a player who was on
     * no other backend lately. The default knows only who is online elsewhere now.
     */
    default Optional<String> cameFrom(UUID player, Duration within) {
        return find(player).map(OnlinePlayer::server).filter(server -> !server.equals(localServer()));
    }

    /** Whether the player with this account is online on any backend this server knows. */
    default boolean isOnline(UUID player) {
        return find(player).isPresent();
    }

    /**
     * Whether the backend named {@code server} is running as far as this server knows: this backend itself, or a peer
     * heard from within the liveness window, timed by when its frames arrived here. A peer this server has not heard
     * from yet is not live.
     *
     * <p>This default can only tell a peer with players on it; the roster that keeps itself current knows every peer
     * it hears from, players or not.
     */
    default boolean isLive(String server) {
        return server.equals(localServer()) || !onServer(server).isEmpty();
    }
}
