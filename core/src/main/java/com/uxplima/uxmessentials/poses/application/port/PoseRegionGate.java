package com.uxplima.uxmessentials.poses.application.port;

import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Outbound gate deciding whether {@code who} may hold {@code type} at {@code where}. Phase 1 wires the permissive
 * {@link com.uxplima.uxmessentials.poses.application.AllowAllRegionGate}; Phase 5 swaps in the real implementation
 * that consults the claim providers and WorldGuard region flags. The use cases depend only on this contract, so
 * that swap is a wiring change with no reach into {@code StartSit} / {@code StartPose}.
 */
public interface PoseRegionGate {

    /** Whether {@code who} may hold {@code type} at {@code where}. */
    boolean canPose(PlayerRef who, Position where, PoseType type);
}
