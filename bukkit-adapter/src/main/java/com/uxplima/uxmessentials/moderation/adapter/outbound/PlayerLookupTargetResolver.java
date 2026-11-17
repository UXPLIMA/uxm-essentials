package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.TargetResolver;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
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
 * <p>On an offline-mode server presence settles only a name spelled exactly as typed. A case variant online is a
 * different account from one spelled as typed offline, so any other name goes to the lookup, which knows every
 * account seen under the name, those online elsewhere included, and calls a name several of them share ambiguous.
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
    public NameMatch resolve(String name) {
        Objects.requireNonNull(name, "name");
        String typed = name.strip();
        Optional<OnlinePlayer> online = presence.findByName(typed);
        if (lookup.nameCase() == NameCase.SENSITIVE) {
            online = online.filter(found -> found.player().name().equals(typed));
        }
        return online.isPresent() ? NameMatch.found(online.get().player()) : lookup.matchByName(typed);
    }
}
