package com.uxplima.uxmessentials.teleport.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SafeLocationQueue;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;

/**
 * The {@code /rtp} use case: serve a pre-validated safe location O(1) from the per-world queue, redirect
 * through the configured fallback world when the requested world has no queue, and hand the destination
 * to the gated teleport machinery. The off-thread safe-search is the queue's refill primitive, fired
 * here below the low-water mark — never an on-demand per-request scan. The requester never waits on a
 * chunk load.
 *
 * <p>Two entry points share the queue: {@link #background} ({@code /rtp}, polls then refills) and
 * {@link #urgent} (respawn / first-join, which falls back to a bounded off-thread search when the queue
 * is empty and is allowed to drain below the background threshold).
 */
public final class ResolveRtp {

    private final SafeLocationQueue queue;
    private final WorldLookup worlds;
    private final TeleportEngine engine;
    private final PlayerNotifier notifier;
    private final TeleportSettings settings;

    public ResolveRtp(
            SafeLocationQueue queue,
            WorldLookup worlds,
            TeleportEngine engine,
            PlayerNotifier notifier,
            TeleportSettings settings) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Background {@code /rtp}: redirect to the fallback world if needed, poll, refill, and teleport. */
    public Result<Unit, TeleportError> background(PlayerRef who, WorldRef requestedWorld) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(requestedWorld, "requestedWorld");
        Optional<WorldRef> resolved = resolveWorld(requestedWorld);
        if (resolved.isEmpty()) {
            notifier.send(who, TeleportMessageKey.RTP_DISALLOWED);
            return Result.err(TeleportError.RTP_WORLD_DISALLOWED);
        }
        WorldRef world = resolved.get();
        Optional<RtpSafeLocation> location = queue.poll(world);
        queue.requestRefill(world);
        return dispatch(who, location, TeleportError.RTP_NO_SAFE_LOCATION);
    }

    /** Urgent path (respawn / first-join): the queue, else a bounded off-thread search; always refills. */
    public Result<Unit, TeleportError> urgent(PlayerRef who, WorldRef requestedWorld) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(requestedWorld, "requestedWorld");
        Optional<WorldRef> resolved = resolveWorld(requestedWorld);
        if (resolved.isEmpty()) {
            return Result.err(TeleportError.RTP_WORLD_DISALLOWED);
        }
        WorldRef world = resolved.get();
        Optional<RtpSafeLocation> location = queue.urgentSearch(world);
        queue.requestRefill(world);
        return dispatch(who, location, TeleportError.RTP_NO_SAFE_LOCATION);
    }

    private Result<Unit, TeleportError> dispatch(
            PlayerRef who, Optional<RtpSafeLocation> location, TeleportError onEmpty) {
        if (location.isEmpty()) {
            notifier.send(who, TeleportMessageKey.RTP_NO_LOCATION);
            return Result.err(onEmpty);
        }
        engine.launch(who, Destination.at(location.get().position()), TeleportKind.RANDOM);
        return Result.ok();
    }

    private Optional<WorldRef> resolveWorld(WorldRef requested) {
        if (queue.hasQueue(requested)) {
            return Optional.of(requested);
        }
        return settings.rtpFallbackWorld(requested).flatMap(worlds::findByName).filter(queue::hasQueue);
    }
}
