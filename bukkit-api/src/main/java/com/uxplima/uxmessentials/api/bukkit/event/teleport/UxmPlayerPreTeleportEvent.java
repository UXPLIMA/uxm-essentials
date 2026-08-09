package com.uxplima.uxmessentials.api.bukkit.event.teleport;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerCancellableEvent;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmTeleportKind;
import org.jspecify.annotations.NullMarked;

/**
 * A player is about to be teleported by uxmEssentials. Cancel to stop it.
 *
 * <p>Every voluntary teleport the plugin performs passes through here: {@code /home}, {@code /warp}, {@code /spawn},
 * {@code /back}, {@code /rtp}, an accepted {@code /tpa}, a staff {@code /tp}, a world entry. The kind tells them
 * apart, so a listener can refuse one and allow the rest.
 *
 * <p>Fired once the plugin's own rules have all passed (not jailed, not in combat, off cooldown, able to pay) and
 * before the warmup starts, so a refusal does not make the player stand still first.
 *
 * <p>An involuntary arrival is deliberately not asked about: a respawn or a first-join drop has to put the player
 * somewhere, and refusing it would leave them nowhere.
 */
@NullMarked
public final class UxmPlayerPreTeleportEvent extends UxmPlayerCancellableEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmTeleportKind kind;
    private final UxmLocation destination;

    public UxmPlayerPreTeleportEvent(UUID playerId, String playerName, UxmTeleportKind kind, UxmLocation destination) {
        super(playerId, playerName);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.destination = Objects.requireNonNull(destination, "destination");
    }

    /** Which kind of teleport it would be. */
    public UxmTeleportKind getKind() {
        return kind;
    }

    /** Where the player would land. */
    public UxmLocation getDestination() {
        return destination;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
