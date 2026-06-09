package com.uxplima.uxmessentials.kits.adapter.inbound.event;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import org.jspecify.annotations.NullMarked;

/**
 * Fired before a kit is claimed. Can be cancelled by other plugins to prevent the claim.
 */
@NullMarked
public final class KitPreClaimEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final KitDefinition kit;
    private boolean cancelled;

    public KitPreClaimEvent(Player player, KitDefinition kit) {
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
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
