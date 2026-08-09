package com.uxplima.uxmessentials.api.bukkit.event.moderation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A player joined from an address another account has used.
 *
 * <p>This is a finding, not a judgement: sharing an address is what a household or a school does as readily as an
 * evader. Whether the join was refused is carried separately, and by the time this arrives it has already been acted
 * on either way.
 */
@NullMarked
public final class UxmAltDetectedEvent extends UxmEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final String ip;
    private final List<UUID> matched;
    private final boolean kicked;

    public UxmAltDetectedEvent(UUID playerId, String ip, List<UUID> matched, boolean kicked) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.ip = Objects.requireNonNull(ip, "ip");
        this.matched = List.copyOf(Objects.requireNonNull(matched, "matched"));
        this.kicked = kicked;
    }

    /** The id of the account that just joined. */
    public UUID getPlayerId() {
        return playerId;
    }

    /** The address it joined from. */
    public String getIp() {
        return ip;
    }

    /** The other accounts seen on that address. Never empty, and never contains the joining account. */
    public List<UUID> getMatched() {
        return matched;
    }

    /** Whether the join was refused because of the match. */
    public boolean isKicked() {
        return kicked;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
