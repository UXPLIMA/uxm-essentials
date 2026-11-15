package com.uxplima.uxmessentials.messaging.application;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.messaging.application.port.MessagingNetwork;
import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.shared.application.RecentIds;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /msg} and {@code /reply} to a player on another backend of the network. The sender's backend decides what it
 * can decide (vanish, mute) and sends the message on; the target's backend applies the target's own switches (message
 * toggle, reply routing, ignore list), delivers it and answers; the sender's backend then tells the sender how it
 * went, with the same lines a message to a player here produces. Social spies on every backend see the message once:
 * the target's backend shows it to its spies as it delivers, every other backend when the answer arrives.
 *
 * <p>A vanished target the sender cannot see is treated as offline, exactly as a vanished player here is, so the
 * sender cannot tell them apart from a player who is gone. A target who left the backend before the message arrived,
 * or whose backend never answers, also takes the offline path: the message becomes mail when offline-to-mail is on,
 * and the sender reads the usual offline line when it is off.
 */
public final class RelayPrivateMessage {

    /**
     * How long the sender's backend waits for the answer before treating the target as gone. A round trip over the
     * bus takes milliseconds; this only ends the wait when the target's backend went down mid-message.
     */
    static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(5);

    private final SendMessage sendMessage;
    private final ReplyRoutingStore replyRouting;
    private final VanishVisibility vanish;
    private final NetworkPresence presence;
    private final MessagingNetwork network;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final Map<UUID, RemoteMessage> awaitingAnswer = new ConcurrentHashMap<>();
    private final RecentIds requests = new RecentIds();
    private final RecentIds answers = new RecentIds();

    public RelayPrivateMessage(
            SendMessage sendMessage,
            ReplyRoutingStore replyRouting,
            VanishVisibility vanish,
            NetworkPresence presence,
            MessagingNetwork network,
            Notifier notifier,
            Scheduler scheduler) {
        this.sendMessage = Objects.requireNonNull(sendMessage, "sendMessage");
        this.replyRouting = Objects.requireNonNull(replyRouting, "replyRouting");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.network = Objects.requireNonNull(network, "network");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Where the online player with this name is, when that is another backend. */
    public Optional<OnlinePlayer> elsewhere(String name) {
        Objects.requireNonNull(name, "name");
        return presence.findByName(name).filter(this::notHere);
    }

    /** Where the player with this account is online, when that is another backend. */
    public Optional<OnlinePlayer> elsewhere(UUID player) {
        Objects.requireNonNull(player, "player");
        return presence.find(player).filter(this::notHere);
    }

    /** {@code /msg}: send {@code body} from {@code sender} to {@code target} on their backend. */
    public void send(PlayerRef sender, OnlinePlayer target, MessageBody body) {
        route(sender, target, body, false);
    }

    /** {@code /reply}: answer {@code target}, the sender's last conversation partner, on their backend. */
    public void reply(PlayerRef sender, OnlinePlayer target, MessageBody body) {
        route(sender, target, body, true);
    }

    private void route(PlayerRef sender, OnlinePlayer target, MessageBody body, boolean reply) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(body, "body");
        if (vanish.isHiddenFrom(sender, target.player())) {
            hidden(sender, target.player(), body, reply);
            return;
        }
        if (sendMessage.refusedMuted(sender)) {
            return;
        }
        RemoteMessage message =
                new RemoteMessage(UUID.randomUUID(), sender, target.player(), target.server(), body, reply);
        awaitingAnswer.put(message.id(), message);
        network.send(message);
        scheduler.asyncAfter(ANSWER_TIMEOUT, () -> giveUp(message.id()));
    }

    private void hidden(PlayerRef sender, PlayerRef target, MessageBody body, boolean reply) {
        // The same feedback a vanished player on this backend produces, so a hidden player elsewhere is never told
        // apart from one who is gone.
        if (reply) {
            notifier.send(sender, MessagingError.NO_REPLY_TARGET.messageKey());
        } else {
            sendMessage.send(sender, target, body, false);
        }
    }

    /**
     * A message from another backend. Only the backend it was sent to acts on it, on the target's thread, and it
     * always answers: a target who has left is answered as offline so the sender is not left waiting.
     */
    public void receive(RemoteMessage message) {
        Objects.requireNonNull(message, "message");
        if (!message.targetServer().equals(presence.localServer()) || !requests.firstTime(message.id())) {
            return;
        }
        scheduler.onEntity(
                message.target(),
                () -> network.answer(sendMessage.receive(message, replyRouting.acceptsReplies(message.target()))),
                () -> network.answer(RemoteAnswer.to(message, RemoteOutcome.OFFLINE)));
    }

    /**
     * The answer to a message. On the sender's backend it tells the sender how the message went; on any other backend
     * it shows the message to the spies there.
     */
    public void receive(RemoteAnswer answer) {
        Objects.requireNonNull(answer, "answer");
        if (!answers.firstTime(answer.id())) {
            return;
        }
        RemoteMessage sent = awaitingAnswer.remove(answer.id());
        if (sent == null) {
            sendMessage.observe(answer);
        } else {
            sendMessage.conclude(sent, answer);
        }
    }

    private void giveUp(UUID id) {
        RemoteMessage sent = awaitingAnswer.remove(id);
        if (sent != null) {
            sendMessage.conclude(sent, RemoteAnswer.to(sent, RemoteOutcome.OFFLINE));
        }
    }

    private boolean notHere(OnlinePlayer player) {
        return !player.server().equals(presence.localServer());
    }
}
