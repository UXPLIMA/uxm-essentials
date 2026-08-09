package com.uxplima.uxmessentials.poses.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.api.bukkit.event.pose.UxmPoseEvent;
import com.uxplima.uxmessentials.api.view.UxmPoseType;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.poses.domain.event.PoseEnded;
import com.uxplima.uxmessentials.poses.domain.event.PoseStarted;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/** Which Bukkit event each pose fact becomes. Both ends of a session share one event with a flag. */
@NullMarked
public final class PoseEventBridges {

    private PoseEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                PoseStarted.class,
                UxmPoseEvent.getHandlerList(),
                fact -> event(fact.session(), true),
                fact -> Region.entity(fact.session().subject()));
        registry.register(
                PoseEnded.class,
                UxmPoseEvent.getHandlerList(),
                fact -> event(fact.session(), false),
                fact -> Region.entity(fact.session().subject()));
    }

    private static UxmPoseEvent event(PoseSession session, boolean started) {
        return new UxmPoseEvent(
                session.subject().uuid(),
                session.subject().name(),
                started,
                type(session.type()),
                ApiValues.location(session.returnLocation()),
                Optional.ofNullable(session.target()).map(target -> target.uuid()),
                session.startedAt());
    }

    private static UxmPoseType type(PoseType type) {
        return switch (type) {
            case SIT -> UxmPoseType.SIT;
            case PLAYER_SIT -> UxmPoseType.PLAYER_SIT;
            case LAY -> UxmPoseType.LAY;
            case BELLYFLOP -> UxmPoseType.BELLYFLOP;
            case SPIN -> UxmPoseType.SPIN;
            case CRAWL -> UxmPoseType.CRAWL;
        };
    }
}
