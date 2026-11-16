package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The kernel {@link PlayerLookup}, with offline-name resolution served from the plugin's own
 * {@link PlayerNameIndex} before it falls back to the server.
 *
 * <p>Order: a connected player first, matched in any case, then the index, then the server's {@link OfflineProfiles}.
 * The middle step is what makes an offline-mode server behave like an online-mode one, where Paper's own name cache
 * is only consulted; the last step never makes a Mojang round-trip, so a name nobody here has used resolves to empty
 * instead of holding the caller.
 *
 * <p>Whether a player is connected, and who a connected player is, is answered from the {@link OnlinePlayerIndex}
 * rather than the server, so a caller on the async lane gets an answer without touching the Bukkit API.
 */
@NullMarked
public final class IndexedPlayerLookup implements PlayerLookup {

    private final OfflineProfiles offline;
    private final PlayerNameIndex index;
    private final OnlinePlayerIndex online;

    public IndexedPlayerLookup(OfflineProfiles offline, PlayerNameIndex index, OnlinePlayerIndex online) {
        this.offline = Objects.requireNonNull(offline, "offline");
        this.index = Objects.requireNonNull(index, "index");
        this.online = Objects.requireNonNull(online, "online");
    }

    @Override
    public Optional<PlayerRef> findOnlineByName(String name) {
        Objects.requireNonNull(name, "name");
        return online.byName(name);
    }

    @Override
    public Optional<PlayerRef> findByName(String name) {
        Objects.requireNonNull(name, "name");
        Optional<PlayerRef> connected = online.byName(name);
        if (connected.isPresent()) {
            return connected;
        }
        Optional<PlayerRef> known = index.byName(name);
        return known.isPresent() ? known : offline.byName(name);
    }

    @Override
    public Optional<PlayerRef> findByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        Optional<PlayerRef> connected = online.byUuid(uuid);
        return connected.isPresent() ? connected : offline.byUuid(uuid);
    }

    @Override
    public boolean isOnline(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return online.isOnline(uuid);
    }
}
