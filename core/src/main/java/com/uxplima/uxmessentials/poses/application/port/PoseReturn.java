package com.uxplima.uxmessentials.poses.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Outbound port that returns a player to where a pose began, for the {@code return-to-start} rule. Ending a pose
 * dismounts the rider at the seat; when the server returns poses to their origin, {@code StopPose} calls this to
 * put the player back exactly where they stood. The adapter performs the region-aware teleport off the port so
 * the use case stays Bukkit-free.
 */
public interface PoseReturn {

    /** Move {@code who} back to {@code where}; a no-op when the player is offline. */
    void returnTo(PlayerRef who, Position where);
}
