package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The connected {@link Player} a typed name names, found through the plugin's {@link PlayerLookup} rather than the
 * server's own by-name methods. Those match any case and keep one entry per lower-cased name, the one who joined last,
 * so on an offline-mode server, where {@code Steve} and {@code steve} can both be online, they can hand back the other
 * player even for an exact spelling.
 */
@NullMarked
public final class OnlineByName {

    private OnlineByName() {}

    /** The connected player {@code name} names, or null for nobody or a name several case variants share. */
    public static @Nullable Player player(PlayerLookup lookup, String name) {
        return player(Objects.requireNonNull(lookup, "lookup").matchOnlineByName(name));
    }

    /** The live player a match names, or null when it names nobody connected. */
    public static @Nullable Player player(NameMatch match) {
        Optional<PlayerRef> found = Objects.requireNonNull(match, "match").player();
        return found.isPresent() ? Bukkit.getPlayer(found.get().uuid()) : null;
    }
}
