package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.StaffTeleportFrame;
import com.uxplima.uxmessentials.shared.network.TeleportAnswerFrame;
import com.uxplima.uxmessentials.shared.network.TeleportRequestFrame;
import com.uxplima.uxmessentials.teleport.application.RemoteTeleport;
import com.uxplima.uxmessentials.teleport.application.port.TeleportNetwork;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.RequestId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Bridges the teleport context's {@link TeleportNetwork} port to the shared bus: each {@link RemoteTeleport} goes out
 * as its frame through the {@link BusPublisher}, and {@link #onFrame}, registered as a remote-sync listener, turns the
 * teleport frames a peer sent back into messages for the subscribed handler on the bus's off-tick dispatch thread.
 * A step or direction name this build does not know (a newer peer during an upgrade) drops the frame and is logged
 * once per name.
 */
@NullMarked
public final class BusTeleportNetwork implements TeleportNetwork {

    private final BusPublisher publisher;
    private final Logger log;
    private final Set<String> reportedUnknown = ConcurrentHashMap.newKeySet();
    private volatile @Nullable Consumer<RemoteTeleport> handler;

    public BusTeleportNetwork(BusPublisher publisher, Logger log) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public String localServer() {
        return publisher.serverId();
    }

    @Override
    public void send(RemoteTeleport message) {
        Objects.requireNonNull(message, "message");
        publisher.publish(frameOf(message));
    }

    @Override
    public void subscribe(Consumer<RemoteTeleport> handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /** Register this with the bus registry: translate a teleport frame and hand it to the subscribed handler. */
    public void onFrame(NetworkMessage frame) {
        Consumer<RemoteTeleport> receiver = handler;
        if (receiver == null) {
            return;
        }
        RemoteTeleport message =
                switch (frame) {
                    case TeleportRequestFrame request -> requestOf(request);
                    case TeleportAnswerFrame answer -> answerOf(answer);
                    case StaffTeleportFrame staff ->
                        new RemoteTeleport.StaffMove(
                                staff.originServer(),
                                staff.requestId(),
                                new PlayerRef(staff.actorUuid(), staff.actorName()),
                                new PlayerRef(staff.subjectUuid(), staff.subjectName()),
                                new PlayerRef(staff.destinationUuid(), staff.destinationName()),
                                staff.subjectServer());
                    default -> null;
                };
        if (message != null) {
            receiver.accept(message);
        }
    }

    private RemoteTeleport.@Nullable Request requestOf(TeleportRequestFrame frame) {
        RequestDirection direction = known(RequestDirection.class, frame.direction());
        if (direction == null || frame.ttlMillis() <= 0) {
            return null;
        }
        return new RemoteTeleport.Request(
                frame.originServer(),
                new RequestId(frame.requestId()),
                new PlayerRef(frame.requesterUuid(), frame.requesterName()),
                new PlayerRef(frame.targetUuid(), frame.targetName()),
                frame.targetServer(),
                direction,
                frame.seeLevel(),
                Duration.ofMillis(frame.ttlMillis()));
    }

    private RemoteTeleport.@Nullable Answer answerOf(TeleportAnswerFrame frame) {
        RemoteTeleport.Step step = known(RemoteTeleport.Step.class, frame.answer());
        if (step == null) {
            return null;
        }
        return new RemoteTeleport.Answer(
                frame.originServer(),
                frame.requestId(),
                step,
                frame.toServer(),
                new PlayerRef(frame.aboutUuid(), frame.aboutName()),
                frame.detail());
    }

    private <E extends Enum<E>> @Nullable E known(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException unknown) {
            if (reportedUnknown.add(type.getSimpleName() + ":" + name)) {
                log.warn("dropping cross-server teleport frames with unknown {} {}", type.getSimpleName(), name);
            }
            return null;
        }
    }

    private static NetworkMessage frameOf(RemoteTeleport message) {
        return switch (message) {
            case RemoteTeleport.Request request ->
                new TeleportRequestFrame(
                        request.fromServer(),
                        request.id().value(),
                        request.requester().uuid(),
                        request.requester().name(),
                        request.target().uuid(),
                        request.target().name(),
                        request.targetServer(),
                        request.direction().name(),
                        request.seeLevel(),
                        request.ttl().toMillis());
            case RemoteTeleport.Answer answer ->
                new TeleportAnswerFrame(
                        answer.fromServer(),
                        answer.id(),
                        answer.step().name(),
                        answer.toServer(),
                        answer.about().uuid(),
                        answer.about().name(),
                        answer.detail());
            case RemoteTeleport.StaffMove staff ->
                new StaffTeleportFrame(
                        staff.fromServer(),
                        staff.id(),
                        staff.actor().uuid(),
                        staff.actor().name(),
                        staff.subject().uuid(),
                        staff.subject().name(),
                        staff.destination().uuid(),
                        staff.destination().name(),
                        staff.subjectServer());
        };
    }
}
