package com.uxplima.uxmessentials.moderation.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** The players connected to one backend, each from one address, and the kick screens they were shown. */
final class OnlinePlayers implements Sanctions {

    private final Map<PlayerRef, String> addresses = new LinkedHashMap<>();
    private final Map<PlayerRef, String> kicked = new LinkedHashMap<>();

    OnlinePlayers connect(PlayerRef player, String address) {
        addresses.put(player, address);
        return this;
    }

    /** Each kicked player and the screen text they read. */
    Map<PlayerRef, String> kicked() {
        return Map.copyOf(kicked);
    }

    @Override
    public void kick(PlayerRef target, MessageKey reasonKey, String reasonText) {
        if (addresses.remove(target) != null) {
            kicked.put(target, reasonText);
        }
    }

    @Override
    public void kickAddress(String ip, Function<PlayerRef, String> reasonFor) {
        List<PlayerRef> matching = addresses.entrySet().stream()
                .filter(entry -> entry.getValue().equals(ip))
                .map(Map.Entry::getKey)
                .toList();
        for (PlayerRef player : matching) {
            kick(player, ModerationMessageKey.BANIP_KICK, reasonFor.apply(player));
        }
    }

    @Override
    public Collection<PlayerRef> onlinePlayers() {
        return List.copyOf(addresses.keySet());
    }

    @Override
    public void freeze(PlayerRef target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unfreeze(PlayerRef target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFrozen(PlayerRef target) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sendToJail(PlayerRef target, String jail) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void releaseFromJail(PlayerRef target) {
        throw new UnsupportedOperationException();
    }
}
