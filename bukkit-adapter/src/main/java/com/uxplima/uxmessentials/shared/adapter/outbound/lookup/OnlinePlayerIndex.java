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
 *
 * <p>Each entry belongs to one session, named by the caller (the live {@code Player} object of that connection). On
 * Folia the quit of an old connection can run after the join of the player's new one, on another thread; only the
 * session that is still current can take the player out.
 */
@NullMarked
public final class OnlinePlayerIndex {

    /** One connection: who it is and the caller's key for it, compared by identity. */
    private record Session(PlayerRef player, Object key) {}

    private final Map<UUID, Session> byId = new ConcurrentHashMap<>();
    private final Map<String, PlayerRef> byName = new ConcurrentHashMap<>();

    /** {@code player} has joined, under the name they joined with, in the connection {@code session}. */
    public void joined(PlayerRef player, Object session) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");
        Session previous = byId.put(player.uuid(), new Session(player, session));
        if (previous != null && !previous.player().name().equals(player.name())) {
            byName.remove(key(previous.player().name()), previous.player());
        }
        byName.put(key(player.name()), player);
    }

    /** The connection {@code session} of the player with this id has ended; a newer connection stays. */
    public void left(UUID uuid, Object session) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(session, "session");
        byId.computeIfPresent(uuid, (id, current) -> {
            if (current.key() != session) {
                return current;
            }
            // Only this account's entry: another account may already hold the name.
            byName.remove(key(current.player().name()), current.player());
            return null;
        });
    }

    /** True while the player with this id is connected. */
    public boolean isOnline(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return byId.containsKey(uuid);
    }

    /** The connected player with this id. */
    public Optional<PlayerRef> byUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return Optional.ofNullable(byId.get(uuid)).map(Session::player);
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
