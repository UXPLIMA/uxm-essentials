package com.uxplima.uxmessentials.api.bukkit.event.teleport;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmTeleportKind;
import org.jspecify.annotations.NullMarked;

/**
 * A player was teleported by uxmEssentials. The move has already happened.
 *
 * <p>Distinct from Bukkit's own {@code PlayerTeleportEvent} in that it fires only for the plugin's own teleports and
 * says which kind it was, so a listener can tell a {@code /home} from an {@code /rtp} without inspecting positions.
 */
@NullMarked
public final class UxmPlayerTeleportEvent extends UxmTeleportEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmTeleportKind kind;
    private final UxmLocation from;
    private final UxmLocation to;

    public UxmPlayerTeleportEvent(
            UUID playerId, String playerName, UxmTeleportKind kind, UxmLocation from, UxmLocation to) {
        super(playerId, playerName);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
    }

    /** Which kind of teleport this was. */
    public UxmTeleportKind getKind() {
        return kind;
    }

    /** Where the player was. */
    public UxmLocation getFrom() {
        return from;
    }

    /** Where the player now is. */
    public UxmLocation getTo() {
        return to;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
