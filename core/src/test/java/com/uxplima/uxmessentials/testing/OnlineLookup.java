package com.uxplima.uxmessentials.testing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** Who is online on this server, as the online index answers it: players join and leave as a test says. */
public final class OnlineLookup implements PlayerLookup {

    private final Map<UUID, PlayerRef> online = new LinkedHashMap<>();

    /** {@code players} are online. */
    public OnlineLookup with(PlayerRef... players) {
        for (PlayerRef player : players) {
            online.put(player.uuid(), player);
        }
        return this;
    }

    /** {@code player} has left. */
    public void leave(PlayerRef player) {
        online.remove(player.uuid());
    }

    @Override
    public Optional<PlayerRef> findOnlineByName(String name) {
        return online.values().stream()
                .filter(player -> player.name().equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public Optional<PlayerRef> findByUuid(UUID uuid) {
        return Optional.ofNullable(online.get(uuid));
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return online.containsKey(uuid);
    }
}
