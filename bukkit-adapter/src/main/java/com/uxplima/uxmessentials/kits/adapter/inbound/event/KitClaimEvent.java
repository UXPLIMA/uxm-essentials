package com.uxplima.uxmessentials.kits.adapter.inbound.event;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import org.jspecify.annotations.NullMarked;

/**
 * Fired when a player claims a kit successfully.
 */
@NullMarked
public final class KitClaimEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final KitDefinition kit;

    public KitClaimEvent(Player player, KitDefinition kit) {
        this.player = Objects.requireNonNull(player, "player");
        this.kit = Objects.requireNonNull(kit, "kit");
    }

    public Player getPlayer() {
        return player;
    }

    public KitDefinition getKit() {
        return kit;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
