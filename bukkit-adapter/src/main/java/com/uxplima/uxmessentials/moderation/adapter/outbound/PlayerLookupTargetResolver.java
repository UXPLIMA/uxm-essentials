package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.TargetResolver;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link TargetResolver} over the kernel's {@link PlayerLookup}, so a sanction target and every other
 * name-to-account resolution in the plugin follow one path: connected player, then the plugin's name index, then
 * the server's offline-player handle.
 *
 * <p>Moderation used to resolve targets itself against {@code Server.getOfflinePlayer(String)}. That reads the
 * server's name cache only on an online-mode server, so on an offline-mode one a name typed in a different case
 * derived a uuid nobody owns and the sanction answered "unknown player" instead of landing.
 */
@NullMarked
public final class PlayerLookupTargetResolver implements TargetResolver {

    private final PlayerLookup lookup;

    public PlayerLookupTargetResolver(PlayerLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    @Override
    public Optional<PlayerRef> resolve(String name) {
        Objects.requireNonNull(name, "name");
        return lookup.findByName(name);
    }
}
