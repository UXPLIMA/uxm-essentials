package com.uxplima.uxmessentials.poses.application;

import com.uxplima.uxmessentials.poses.application.port.PoseRegionGate;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * The permissive {@link PoseRegionGate} wired in Phase 1: posing is allowed everywhere. Phase 5 replaces it with
 * the claim- and WorldGuard-aware gate; keeping the seam here from the start means that swap never touches the use
 * cases. A server with no region plugin also keeps this behaviour — a pose is allowed when nothing forbids it.
 */
public final class AllowAllRegionGate implements PoseRegionGate {

    @Override
    public boolean canPose(PlayerRef who, Position where, PoseType type) {
        return true;
    }
}
