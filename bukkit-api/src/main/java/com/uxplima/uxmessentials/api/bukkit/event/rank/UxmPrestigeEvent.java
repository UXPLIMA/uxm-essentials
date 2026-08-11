package com.uxplima.uxmessentials.api.bukkit.event.rank;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A player prestiged: they were at the top of the ladder, paid the prestige cost, and went back to the first rung
 * one level higher.
 *
 * <p>Fires after the reset is stored. The rank a handler reads is therefore the first rung, not the top one they
 * were on a moment earlier.
 */
@NullMarked
public final class UxmPrestigeEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int level;
    private final double rewardMultiplier;

    public UxmPrestigeEvent(UUID playerId, String playerName, int level, double rewardMultiplier) {
        super(playerId, playerName);
        this.level = level;
        this.rewardMultiplier = rewardMultiplier;
    }

    /** The prestige level they reached. */
    public int getLevel() {
        return level;
    }

    /** The reward multiplier that level earns them. */
    public double getRewardMultiplier() {
        return rewardMultiplier;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
