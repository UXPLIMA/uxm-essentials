package com.uxplima.uxmessentials.poses.application.port;

import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Outbound gate deciding whether {@code who} may hold {@code type} at {@code where}. Production wires the
 * {@link com.uxplima.uxmessentials.poses.application.ClaimAwareRegionGate}, which consults land claims (through the
 * shared {@code ClaimService}) and WorldGuard region flags behind the {@code respect-claims} /
 * {@code respect-worldguard} toggles; the permissive
 * {@link com.uxplima.uxmessentials.poses.application.AllowAllRegionGate} is the "nothing forbids a pose" default a
 * server with no region plugin also lands on, and the fixture the tests gate with. The use cases depend only on this
 * contract, so which gate is wired is invisible to {@code StartSit} / {@code StartPose} / {@code StartCrawl} /
 * {@code StartPlayerSit}.
 */
public interface PoseRegionGate {

    /** Whether {@code who} may hold {@code type} at {@code where}. */
    boolean canPose(PlayerRef who, Position where, PoseType type);
}
