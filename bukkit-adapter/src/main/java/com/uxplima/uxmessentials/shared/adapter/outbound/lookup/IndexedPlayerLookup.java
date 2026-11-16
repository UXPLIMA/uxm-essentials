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
 * <p>Order: a connected player first (Paper matches an online name without minding its case), then the index,
 * then the server's offline-player handle. The middle step is what makes an offline-mode server behave like an
 * online-mode one, where Paper's own name cache is only consulted; on an online-mode server it additionally
 * spares the command path a blocking Mojang round-trip for a name the plugin has already seen.
 *
 * <p>Whether a player is connected, and who a connected player is, is answered from the {@link OnlinePlayerIndex}
 * rather than the server, so a caller on the async lane gets an answer without touching the Bukkit API.
 */
@NullMarked
public final class IndexedPlayerLookup implements PlayerLookup {

    private final PlayerLookup delegate;
    private final PlayerNameIndex index;
    private final OnlinePlayerIndex online;

    public IndexedPlayerLookup(PlayerLookup delegate, PlayerNameIndex index, OnlinePlayerIndex online) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
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
        return known.isPresent() ? known : delegate.findByName(name);
    }

    @Override
    public Optional<PlayerRef> findByUuid(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        Optional<PlayerRef> connected = online.byUuid(uuid);
        return connected.isPresent() ? connected : delegate.findByUuid(uuid);
    }

    @Override
    public boolean isOnline(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return online.isOnline(uuid);
    }
}
