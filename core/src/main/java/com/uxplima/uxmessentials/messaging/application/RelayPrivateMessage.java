package com.uxplima.uxmessentials.messaging.application;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

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
 * can decide (mute, and vanish as far as its network view knows) and sends the message on with the sender's vanish see
 * level; the target's backend applies the target's own vanish and switches (message toggle, reply routing, ignore
 * list), delivers it and answers; the sender's backend then tells the sender how it went, with the same lines a
 * message to a player here produces. Social spies on every backend see the message once: the target's backend shows it
 * to its spies as it delivers, every other backend when the answer arrives.
 *
 * <p>A vanished target the sender cannot see is answered as offline, exactly as a vanished player here is, so the
 * sender cannot tell them apart from a player who is gone; the target's backend checks this itself, because the
 * sender's backend knows peers' vanish only while vanish {@code cross-server} is on. A target who left before the
 * message arrived, or a backend that runs private messages through another plugin, answers offline too: the message
 * becomes mail when offline-to-mail is on, and the sender reads the usual offline line when it is off. A backend that
 * never answers leaves the sender with the offline line and no mail, since the target may still have read it.
 *
 * <p>Answers arrive on the bus's dispatch thread. What the sender reads is written on the sender's thread and what
 * spies read on the global thread, because both resolve online players; mail is written on the async lane.
 */
public final class RelayPrivateMessage {

    /**
     * How long the sender's backend waits for the answer before telling the sender the target is unreachable. A round
     * trip over the bus takes milliseconds; this only ends the wait when the target's backend went down mid-message or
     * does not run private messages.
     */
    static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(5);

    private final SendMessage sendMessage;
    private final ReplyRoutingStore replyRouting;
    private final VanishVisibility vanish;
    private final NetworkPresence presence;
    private final MessagingNetwork network;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final BooleanSupplier steppedBack;
    private final Map<UUID, RemoteMessage> awaitingAnswer = new ConcurrentHashMap<>();
    private final Map<UUID, RemoteMessage> overdue = new ConcurrentHashMap<>();
    private final RecentIds requests = new RecentIds();
    private final RecentIds answers = new RecentIds();

    /**
     * @param steppedBack whether another plugin runs private messages on this backend; its ignore lists and toggles
     *     are not ours to read, so a message from another backend is answered as offline here
     */
    public RelayPrivateMessage(
            SendMessage sendMessage,
            ReplyRoutingStore replyRouting,
            VanishVisibility vanish,
            NetworkPresence presence,
            MessagingNetwork network,
            Notifier notifier,
            Scheduler scheduler,
            BooleanSupplier steppedBack) {
        this.sendMessage = Objects.requireNonNull(sendMessage, "sendMessage");
        this.replyRouting = Objects.requireNonNull(replyRouting, "replyRouting");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.network = Objects.requireNonNull(network, "network");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.steppedBack = Objects.requireNonNull(steppedBack, "steppedBack");
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
        RemoteMessage message = new RemoteMessage(
                UUID.randomUUID(), sender, vanish.seeLevel(sender), target.player(), target.server(), body, reply);
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
     * always answers: a target who has left, or who is vanished from the sender, is answered as offline so the sender
     * is not left waiting and cannot tell the two apart.
     */
    public void receive(RemoteMessage message) {
        Objects.requireNonNull(message, "message");
        if (!message.targetServer().equals(presence.localServer()) || !requests.firstTime(message.id())) {
            return;
        }
        if (steppedBack.getAsBoolean()) {
            network.answer(RemoteAnswer.to(message, RemoteOutcome.OFFLINE));
            return;
        }
        scheduler.onEntity(
                message.target(),
                () -> network.answer(deliverHere(message)),
                () -> network.answer(RemoteAnswer.to(message, RemoteOutcome.OFFLINE)));
    }

    private RemoteAnswer deliverHere(RemoteMessage message) {
        if (vanish.isHiddenHereFrom(message.sender(), message.senderSeeLevel(), message.target())) {
            return RemoteAnswer.to(message, RemoteOutcome.OFFLINE);
        }
        return sendMessage.receive(message, replyRouting.acceptsReplies(message.target()));
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
        boolean late = false;
        if (sent == null) {
            sent = overdue.remove(answer.id());
            late = sent != null;
        }
        if (sent == null) {
            scheduler.onGlobal(() -> sendMessage.observe(answer));
            return;
        }
        if (answer.outcome() == RemoteOutcome.OFFLINE) {
            // A late offline answer changes nothing: the sender already read the offline line when the wait ended.
            if (!late) {
                concludeOffline(sent);
            }
            return;
        }
        RemoteMessage message = sent;
        scheduler.onEntity(
                message.sender(),
                () -> sendMessage.conclude(message, answer),
                () -> scheduler.onGlobal(() -> sendMessage.observe(answer)));
    }

    private void concludeOffline(RemoteMessage sent) {
        if (sent.reply()) {
            scheduler.onEntity(
                    sent.sender(), () -> sendMessage.conclude(sent, RemoteAnswer.to(sent, RemoteOutcome.OFFLINE)));
        } else {
            // Mail is storage work, so it stays on the async lane.
            scheduler.async(() -> sendMessage.offline(sent.sender(), sent.target(), sent.body()));
        }
    }

    private void giveUp(UUID id) {
        RemoteMessage sent = awaitingAnswer.remove(id);
        if (sent == null) {
            return;
        }
        // Kept a while longer, so an answer that was only slow still tells the sender what really happened.
        overdue.put(id, sent);
        scheduler.asyncAfter(ANSWER_TIMEOUT, () -> overdue.remove(id));
        scheduler.onEntity(
                sent.sender(), () -> sendMessage.conclude(sent, RemoteAnswer.to(sent, RemoteOutcome.OFFLINE)));
    }

    private boolean notHere(OnlinePlayer player) {
        return !player.server().equals(presence.localServer());
    }
}
