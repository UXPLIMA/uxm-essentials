package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.ArrayList;
import java.util.List;
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
 * charging, messaging or hopping. A name is matched in any case, and every connected account under it is kept: on
 * an offline-mode server {@code Steve} and {@code steve} can be online at once, and the lookup decides which one a
 * typed name means.
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
    /** Each lower-cased name to the accounts connected under it, in the order they joined; lists are never mutated. */
    private final Map<String, List<PlayerRef>> byName = new ConcurrentHashMap<>();

    /** {@code player} has joined, under the name they joined with, in the connection {@code session}. */
    public void joined(PlayerRef player, Object session) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");
        Session previous = byId.put(player.uuid(), new Session(player, session));
        if (previous != null) {
            unlink(previous.player());
        }
        byName.compute(key(player.name()), (name, current) -> {
            List<PlayerRef> next = current == null ? new ArrayList<>() : new ArrayList<>(current);
            next.remove(player);
            next.add(player);
            return List.copyOf(next);
        });
    }

    /** The connection {@code session} of the player with this id has ended; a newer connection stays. */
    public void left(UUID uuid, Object session) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(session, "session");
        byId.computeIfPresent(uuid, (id, current) -> {
            if (current.key() != session) {
                return current;
            }
            // Only this account's entry: another account may hold the name too.
            unlink(current.player());
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

    /** Every connected player with this name in any case, in the order they joined. */
    public List<PlayerRef> byName(String name) {
        Objects.requireNonNull(name, "name");
        return byName.getOrDefault(key(name.strip()), List.of());
    }

    /** Take one account out from under its name, leaving any other account connected under it. */
    private void unlink(PlayerRef player) {
        byName.computeIfPresent(key(player.name()), (name, current) -> {
            List<PlayerRef> next = new ArrayList<>(current);
            // PlayerRef equality is the account, so this removes this account whatever name it carried.
            next.remove(player);
            return next.isEmpty() ? null : List.copyOf(next);
        });
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
