package com.uxplima.uxmessentials.teleport.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmBackLocationCaptureEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmPlayerPreTeleportEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmPlayerTeleportEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmTeleportRequestAcceptEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmTeleportRequestCancelEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmTeleportRequestDenyEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmTeleportRequestExpireEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmTeleportRequestSendEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmWarmupCancelEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmWarmupStartEvent;
import com.uxplima.uxmessentials.api.view.UxmBackCause;
import com.uxplima.uxmessentials.api.view.UxmTeleportKind;
import com.uxplima.uxmessentials.api.view.UxmTeleportRequestDirection;
import com.uxplima.uxmessentials.api.view.UxmWarmupCancelReason;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.VetoRegistry;
import com.uxplima.uxmessentials.teleport.domain.BackCause;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelReason;
import com.uxplima.uxmessentials.teleport.domain.event.BackLocationCaptured;
import com.uxplima.uxmessentials.teleport.domain.event.PlayerTeleported;
import com.uxplima.uxmessentials.teleport.domain.event.PlayerTeleporting;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequestAccepted;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequestCancelled;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequestDenied;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequestExpired;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequested;
import com.uxplima.uxmessentials.teleport.domain.event.WarmupCancelled;
import com.uxplima.uxmessentials.teleport.domain.event.WarmupStarted;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each teleport fact becomes.
 *
 * <p>A completed teleport is delivered at the destination rather than on the player's entity, because that is the
 * region a listener has to be on to touch the world the player just arrived in. Everything else follows the player
 * it is about; a request event follows the requester, who is the one waiting on an answer.
 */
@NullMarked
public final class TeleportEventBridges {

    private TeleportEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                PlayerTeleported.class,
                UxmPlayerTeleportEvent.getHandlerList(),
                fact -> new UxmPlayerTeleportEvent(
                        fact.player().uuid(),
                        fact.player().name(),
                        kind(fact.kind()),
                        ApiValues.location(fact.from()),
                        ApiValues.location(fact.to())),
                fact -> Region.at(fact.to()));
        registry.register(
                BackLocationCaptured.class,
                UxmBackLocationCaptureEvent.getHandlerList(),
                fact -> new UxmBackLocationCaptureEvent(
                        fact.player().uuid(),
                        fact.player().name(),
                        ApiValues.location(fact.position()),
                        cause(fact.cause())),
                fact -> Region.entity(fact.player()));
        registry.register(
                WarmupStarted.class,
                UxmWarmupStartEvent.getHandlerList(),
                fact -> new UxmWarmupStartEvent(
                        fact.player().uuid(),
                        fact.player().name(),
                        kind(fact.kind()),
                        ApiValues.location(fact.origin()),
                        fact.duration()),
                fact -> Region.entity(fact.player()));
        registry.register(
                WarmupCancelled.class,
                UxmWarmupCancelEvent.getHandlerList(),
                fact -> new UxmWarmupCancelEvent(
                        fact.player().uuid(), fact.player().name(), kind(fact.kind()), reason(fact.reason())),
                fact -> Region.entity(fact.player()));
        registry.register(
                TeleportRequested.class,
                UxmTeleportRequestSendEvent.getHandlerList(),
                fact -> new UxmTeleportRequestSendEvent(
                        fact.requestId().value(),
                        fact.requester().uuid(),
                        fact.requester().name(),
                        fact.target().uuid(),
                        fact.target().name(),
                        direction(fact.direction()),
                        fact.expiresAt()),
                fact -> Region.entity(fact.requester()));
        registry.register(
                TeleportRequestAccepted.class,
                UxmTeleportRequestAcceptEvent.getHandlerList(),
                fact -> new UxmTeleportRequestAcceptEvent(
                        fact.requestId().value(),
                        fact.requester().uuid(),
                        fact.requester().name(),
                        fact.target().uuid(),
                        fact.target().name()),
                fact -> Region.entity(fact.requester()));
        registry.register(
                TeleportRequestDenied.class,
                UxmTeleportRequestDenyEvent.getHandlerList(),
                fact -> new UxmTeleportRequestDenyEvent(
                        fact.requestId().value(),
                        fact.requester().uuid(),
                        fact.requester().name(),
                        fact.target().uuid(),
                        fact.target().name()),
                fact -> Region.entity(fact.requester()));
        registry.register(
                TeleportRequestCancelled.class,
                UxmTeleportRequestCancelEvent.getHandlerList(),
                fact -> new UxmTeleportRequestCancelEvent(
                        fact.requestId().value(),
                        fact.requester().uuid(),
                        fact.requester().name(),
                        fact.target().uuid(),
                        fact.target().name()),
                fact -> Region.entity(fact.requester()));
        registry.register(
                TeleportRequestExpired.class,
                UxmTeleportRequestExpireEvent.getHandlerList(),
                fact -> new UxmTeleportRequestExpireEvent(
                        fact.requestId().value(),
                        fact.requester().uuid(),
                        fact.requester().name(),
                        fact.target().uuid(),
                        fact.target().name()),
                fact -> Region.entity(fact.requester()));
    }

    /** The one teleport action worth refusing: the move itself, whatever asked for it. */
    public static void registerVetoes(VetoRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                PlayerTeleporting.class,
                UxmPlayerPreTeleportEvent.getHandlerList(),
                proposal -> new UxmPlayerPreTeleportEvent(
                        proposal.player().uuid(),
                        proposal.player().name(),
                        kind(proposal.kind()),
                        ApiValues.location(proposal.to())));
    }

    // Exhaustive switches rather than valueOf on the name: a new domain constant then stops this compiling until
    // somebody decides what the published vocabulary should call it, instead of throwing at runtime on a live server.
    private static UxmTeleportKind kind(TeleportKind kind) {
        return switch (kind) {
            case REQUEST -> UxmTeleportKind.REQUEST;
            case BACK -> UxmTeleportKind.BACK;
            case RANDOM -> UxmTeleportKind.RANDOM;
            case SPAWN -> UxmTeleportKind.SPAWN;
            case HOME -> UxmTeleportKind.HOME;
            case WARP -> UxmTeleportKind.WARP;
            case RESPAWN -> UxmTeleportKind.RESPAWN;
            case ADMIN -> UxmTeleportKind.ADMIN;
            case POSITIONAL -> UxmTeleportKind.POSITIONAL;
        };
    }

    private static UxmBackCause cause(BackCause cause) {
        return switch (cause) {
            case TELEPORT -> UxmBackCause.TELEPORT;
            case DEATH -> UxmBackCause.DEATH;
        };
    }

    private static UxmWarmupCancelReason reason(WarmupCancelReason reason) {
        return switch (reason) {
            case MOVED -> UxmWarmupCancelReason.MOVED;
            case ROTATED -> UxmWarmupCancelReason.ROTATED;
            case DAMAGED -> UxmWarmupCancelReason.DAMAGED;
            case INTERACTED -> UxmWarmupCancelReason.INTERACTED;
            case ABORTED -> UxmWarmupCancelReason.ABORTED;
        };
    }

    private static UxmTeleportRequestDirection direction(RequestDirection direction) {
        return switch (direction) {
            case TO_TARGET -> UxmTeleportRequestDirection.TO_TARGET;
            case TO_REQUESTER -> UxmTeleportRequestDirection.TO_REQUESTER;
        };
    }
}
