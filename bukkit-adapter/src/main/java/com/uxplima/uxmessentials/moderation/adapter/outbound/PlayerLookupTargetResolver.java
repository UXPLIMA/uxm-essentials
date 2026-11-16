package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.TargetResolver;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link TargetResolver} over network presence and the kernel's {@link PlayerLookup}, so a sanction target and
 * every other name-to-account resolution in the plugin follow one path: a player online on any backend, then the
 * plugin's name index, then the server's offline-player handle.
 *
 * <p>Presence comes first because an online player's account is certain while the offline cache can hold a stale
 * owner of the name: on another backend a renamed or case-variant account may hold the name now, and resolving it
 * through the cache would sanction the wrong account.
 *
 * <p>Moderation does not resolve targets against {@code Server.getOfflinePlayer(String)}. That reads the server's
 * name cache only on an online-mode server, so on an offline-mode one a name typed in a different case derives a
 * uuid nobody owns and the sanction would answer "unknown player" instead of landing.
 */
@NullMarked
public final class PlayerLookupTargetResolver implements TargetResolver {

    private final NetworkPresence presence;
    private final PlayerLookup lookup;

    public PlayerLookupTargetResolver(NetworkPresence presence, PlayerLookup lookup) {
        this.presence = Objects.requireNonNull(presence, "presence");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    @Override
    public Optional<PlayerRef> resolve(String name) {
        Objects.requireNonNull(name, "name");
        Optional<PlayerRef> online = presence.findByName(name).map(OnlinePlayer::player);
        return online.isPresent() ? online : lookup.findByName(name);
    }
}
