package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Who is connected to this server, kept from the join and quit events so any thread can ask.
 *
 * <p>Asking the server instead ({@code Bukkit.getPlayer}) is legal only on a tick thread, and most of the questions
 * come from the async lane, after a database read, where the answer decides whether a player is still worth
 * charging, messaging or hopping. A name is matched in any case, as the server matches an online name.
 */
@NullMarked
public final class OnlinePlayerIndex {

    private final Map<UUID, PlayerRef> byId = new ConcurrentHashMap<>();
    private final Map<String, PlayerRef> byName = new ConcurrentHashMap<>();

    /** {@code player} has joined, under the name they joined with. */
    public void joined(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        PlayerRef previous = byId.put(player.uuid(), player);
        if (previous != null && !previous.name().equals(player.name())) {
            byName.remove(key(previous.name()), previous);
        }
        byName.put(key(player.name()), player);
    }

    /** The player with this id has left. */
    public void left(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        PlayerRef gone = byId.remove(uuid);
        if (gone != null) {
            // Only this account's entry: another account may already hold the name.
            byName.remove(key(gone.name()), gone);
        }
    }

    /** True while the player with this id is connected. */
    public boolean isOnline(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return byId.containsKey(uuid);
    }

    /** The connected player with this id. */
    public Optional<PlayerRef> byUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return Optional.ofNullable(byId.get(uuid));
    }

    /** The connected player with this name, in any case. */
    public Optional<PlayerRef> byName(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(byName.get(key(name)));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
