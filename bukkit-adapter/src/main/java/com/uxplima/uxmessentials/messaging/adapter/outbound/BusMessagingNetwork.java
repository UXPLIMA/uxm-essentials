package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.messaging.application.HelpOp;
import com.uxplima.uxmessentials.messaging.application.RelayPrivateMessage;
import com.uxplima.uxmessentials.messaging.application.RemoteAnswer;
import com.uxplima.uxmessentials.messaging.application.RemoteHelpOp;
import com.uxplima.uxmessentials.messaging.application.RemoteMessage;
import com.uxplima.uxmessentials.messaging.application.RemoteOutcome;
import com.uxplima.uxmessentials.messaging.application.port.MessagingNetwork;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.HelpOpFrame;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.PrivateMessageAnswerFrame;
import com.uxplima.uxmessentials.shared.network.PrivateMessageFrame;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link MessagingNetwork} over the shared cross-server bus: private messages, their answers and help requests
 * leave as frames through the {@link BusPublisher}, and the frames peers send come back through {@link #onFrame},
 * which the wiring registers with the bus. That runs on the bus's off-tick dispatch thread; the use cases it feeds do
 * their own hop to a player's or the global thread before they touch the server.
 *
 * <p>The use cases are bound after construction because they hold this port themselves. A frame that arrives before
 * the binding is dropped, which can only happen in the moment the module starts.
 */
@NullMarked
public final class BusMessagingNetwork implements MessagingNetwork {

    private final BusPublisher publisher;
    private final Logger log;
    private volatile @Nullable RelayPrivateMessage relay;
    private volatile @Nullable HelpOp helpOp;

    /** Outcomes already reported as unknown: a peer on a newer build sends them with every answer during an upgrade. */
    private final Set<String> reportedUnknown = ConcurrentHashMap.newKeySet();

    public BusMessagingNetwork(BusPublisher publisher, Logger log) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Hand inbound traffic to these use cases from now on. */
    public void bind(RelayPrivateMessage relay, HelpOp helpOp) {
        this.relay = Objects.requireNonNull(relay, "relay");
        this.helpOp = Objects.requireNonNull(helpOp, "helpOp");
    }

    @Override
    public void send(RemoteMessage message) {
        Objects.requireNonNull(message, "message");
        publisher.publish(new PrivateMessageFrame(
                publisher.serverId(),
                message.id(),
                message.targetServer(),
                message.sender().uuid(),
                message.sender().name(),
                message.senderSeeLevel(),
                message.target().uuid(),
                message.target().name(),
                message.body().value(),
                message.reply()));
    }

    @Override
    public void answer(RemoteAnswer answer) {
        Objects.requireNonNull(answer, "answer");
        publisher.publish(new PrivateMessageAnswerFrame(
                publisher.serverId(),
                answer.id(),
                answer.outcome().name(),
                answer.sender().uuid(),
                answer.sender().name(),
                answer.target().uuid(),
                answer.target().name(),
                answer.body().value(),
                answer.afkReason().isPresent(),
                answer.afkReason().orElse("")));
    }

    @Override
    public void raise(RemoteHelpOp request) {
        Objects.requireNonNull(request, "request");
        publisher.publish(new HelpOpFrame(
                publisher.serverId(),
                request.id(),
                request.requester().uuid(),
                request.requester().name(),
                request.body().value()));
    }

    /** Register this with the bus registry: turn a peer's messaging frame into a use case call. */
    public void onFrame(NetworkMessage message) {
        RelayPrivateMessage boundRelay = relay;
        HelpOp boundHelpOp = helpOp;
        if (boundRelay == null || boundHelpOp == null) {
            return;
        }
        switch (message) {
            case PrivateMessageFrame frame -> onMessage(boundRelay, frame);
            case PrivateMessageAnswerFrame frame -> onAnswer(boundRelay, frame);
            case HelpOpFrame frame -> onHelpOp(boundHelpOp, frame);
            default -> {
                // A frame of another context.
            }
        }
    }

    private void onMessage(RelayPrivateMessage boundRelay, PrivateMessageFrame frame) {
        body(frame.body())
                .ifPresent(body -> boundRelay.receive(new RemoteMessage(
                        frame.messageId(),
                        new PlayerRef(frame.senderUuid(), frame.senderName()),
                        frame.senderSeeLevel(),
                        new PlayerRef(frame.targetUuid(), frame.targetName()),
                        frame.targetServer(),
                        body,
                        frame.reply())));
    }

    private void onAnswer(RelayPrivateMessage boundRelay, PrivateMessageAnswerFrame frame) {
        RemoteOutcome outcome;
        try {
            outcome = RemoteOutcome.valueOf(frame.outcome());
        } catch (IllegalArgumentException unknown) {
            // The sender's backend falls back to the offline path when its wait ends.
            if (reportedUnknown.add(frame.outcome())) {
                log.warn("dropping cross-server private message answers with unknown outcome {}", frame.outcome());
            }
            return;
        }
        body(frame.body())
                .ifPresent(body -> boundRelay.receive(new RemoteAnswer(
                        frame.messageId(),
                        outcome,
                        new PlayerRef(frame.senderUuid(), frame.senderName()),
                        new PlayerRef(frame.targetUuid(), frame.targetName()),
                        body,
                        frame.afk() ? Optional.of(frame.afkReason()) : Optional.empty())));
    }

    private void onHelpOp(HelpOp boundHelpOp, HelpOpFrame frame) {
        body(frame.body())
                .ifPresent(body -> boundHelpOp.receive(
                        new RemoteHelpOp(
                                frame.requestId(), new PlayerRef(frame.requesterUuid(), frame.requesterName()), body),
                        frame.originServer()));
    }

    private Optional<MessageBody> body(String text) {
        try {
            return Optional.of(MessageBody.of(text));
        } catch (IllegalArgumentException invalid) {
            // Only a peer with another length limit sends one, and showing it cut short would change what was said.
            log.warn(
                    "dropping a cross-server message whose body this server refuses: {}",
                    String.valueOf(invalid.getMessage()));
            return Optional.empty();
        }
    }
}
