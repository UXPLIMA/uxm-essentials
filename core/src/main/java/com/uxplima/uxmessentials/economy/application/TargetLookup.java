package com.uxplima.uxmessentials.economy.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * Who a name typed into an economy command means, read off the tick, where the lookup may read the database or the
 * server's player data. A lookup that fails is reported to the player who typed the name, who would otherwise hear
 * nothing at all, and then rethrown so the async lane logs its cause.
 */
public final class TargetLookup {

    private final PlayerLookup players;
    private final EconomyNotifier notifier;

    public TargetLookup(PlayerLookup players, EconomyNotifier notifier) {
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Who {@code name} means; {@code viewer} reads {@code failed}, with the name as {@code {player}}, if it fails. */
    public NameMatch find(PlayerRef viewer, String name, MessageKey failed) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(failed, "failed");
        try {
            return players.matchByName(name);
        } catch (RuntimeException unreadable) {
            notifier.send(viewer, failed, Map.of("player", name));
            throw unreadable;
        }
    }
}
