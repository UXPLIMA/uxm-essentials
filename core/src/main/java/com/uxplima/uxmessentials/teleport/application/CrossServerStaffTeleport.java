package com.uxplima.uxmessentials.teleport.application;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.application.RemoteTeleport.Step;
import com.uxplima.uxmessentials.teleport.application.port.RequestVisibility;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportNetwork;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.TeleportMessageKey;

/**
 * Staff {@code /tp} when one of the two players is on another backend. The backend the subject is on moves them:
 * to a destination player on the same backend directly, as a local staff teleport does, and to one on another
 * backend through the shared hop as {@link HopKind#PLAYER_STAFF}, which the gate rules as a local staff teleport and
 * whose landing reads the destination's live position there. A subject on another backend is reached with a staff
 * move message; that backend reports back, and the staff member reads one line for the outcome, or a no-answer line
 * when nothing came back in {@link CrossServerRequests#ANSWER_GRACE}.
 *
 * <p>Vanish holds across servers as it does here: a subject or destination vanished above the staff member's see level
 * reads as offline to them, and nobody is moved. The backend a player is on judges them by its own vanish state; a
 * destination on a third backend is judged by its shared vanish row before anyone is sent, so a hidden player's
 * server is never revealed by the move, and again by its own backend at landing.
 *
 * <p>A repeated move message is acted on once, and an answer is read once, whichever copy arrives first.
 */
public final class CrossServerStaffTeleport {

    /** How long the subject's backend remembers a move it acted on, longer than any repeat can lag behind. */
    private static final Duration REMEMBERED = Duration.ofMinutes(1);

    /** A staff move this backend sent, waiting for the subject's backend to say how it went. */
    private record Pending(PlayerRef actor, PlayerRef subject, PlayerRef destination) {}

    /**
     * A player named in a staff teleport: either one the command's selector found on this backend, or a plain name it
     * found nobody for here. A name the selector missed is never taken to mean a player here, since the selector may
     * have hidden them (a vanished player) and the network lookup must not reveal them.
     *
     * @param name the player's name
     * @param here true when the selector resolved the player on this backend
     */
    public record Named(String name, boolean here) {

        public Named {
            Objects.requireNonNull(name, "name");
        }

        /** A player the selector found on this backend. */
        public static Named here(String name) {
            return new Named(name, true);
        }

        /** A plain name the selector found nobody for on this backend. */
        public static Named elsewhere(String name) {
            return new Named(name, false);
        }
    }

    private final TeleportNetwork network;
    private final NetworkPresence presence;
    private final PlayerLookup players;
    private final PlayerLocator locator;
    private final RequestVisibility visibility;
    private final PlayerHops hops;
    private final TeleportExecutor executor;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final Logger log;

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> unanswered = new ConcurrentHashMap<>();
    private final Set<UUID> actedOn = ConcurrentHashMap.newKeySet();

    public CrossServerStaffTeleport(
            TeleportNetwork network,
            NetworkPresence presence,
            PlayerLookup players,
            PlayerLocator locator,
            RequestVisibility visibility,
            PlayerHops hops,
            TeleportExecutor executor,
            Notifier notifier,
            Scheduler scheduler,
            Logger log) {
        this.network = Objects.requireNonNull(network, "network");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.players = Objects.requireNonNull(players, "players");
        this.locator = Objects.requireNonNull(locator, "locator");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.hops = Objects.requireNonNull(hops, "hops");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** True while staff teleports may reach players on other backends. */
    public boolean enabled() {
        return hops.enabled();
    }

    /** {@code /tp <player>}: move {@code actor}, a player on this backend, to the named player on another one. */
    public void goTo(PlayerRef actor, String destinationName) {
        Objects.requireNonNull(actor, "actor");
        move(actor, Named.here(actor.name()), Named.elsewhere(destinationName));
    }

    /**
     * {@code /tp <subject> <destination>} (and {@code /tphere}, with the actor as the destination) where at least one
     * of the two is on another backend. Each name is looked up across the network; one nobody has online reads as
     * offline to the actor.
     */
    public void move(PlayerRef actor, Named subjectName, Named destinationName) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subjectName, "subjectName");
        Objects.requireNonNull(destinationName, "destinationName");
        Optional<OnlinePlayer> subject = locate(subjectName);
        Optional<OnlinePlayer> destination = locate(destinationName);
        if (subject.isEmpty() || !enabled()) {
            offline(actor, subjectName.name());
            return;
        }
        if (destination.isEmpty()) {
            offline(actor, destinationName.name());
            return;
        }
        PlayerRef mover = subject.get().player();
        PlayerRef anchor = destination.get().player();
        int seeLevel = visibility.seeLevel(actor);
        if (isHere(subject.get())) {
            boolean self = mover.equals(actor);
            moveFromHere(
                    actor,
                    seeLevel,
                    mover,
                    destination.get(),
                    () -> notifier.send(actor, TeleportMessageKey.TP_DONE),
                    server -> {
                        // The mover reads the hop's own sending line; a staff member moving someone else reads this.
                        if (!self) {
                            notifier.send(
                                    actor, TeleportMessageKey.TP_CROSS_SERVER_SENT, sentLine(mover, anchor, server));
                        }
                    },
                    () -> {
                        if (!self) {
                            notifier.send(
                                    actor, TeleportMessageKey.TP_CROSS_SERVER_REFUSED, Map.of("player", mover.name()));
                        }
                    },
                    () -> offline(actor, mover.name()),
                    () -> offline(actor, anchor.name()));
            return;
        }
        UUID id = UUID.randomUUID();
        pending.put(id, new Pending(actor, mover, anchor));
        network.send(new RemoteTeleport.StaffMove(
                network.localServer(), id, actor, mover, anchor, subject.get().server(), seeLevel));
        later(CrossServerRequests.ANSWER_GRACE, () -> {
            Pending waited = pending.remove(id);
            if (waited != null) {
                notifier.send(
                        waited.actor(),
                        TeleportMessageKey.TP_CROSS_SERVER_NO_ANSWER,
                        Map.of("player", waited.subject().name()));
                // An answer may still be on its way; it then tells the staff member what really happened.
                unanswered.put(id, waited);
                later(REMEMBERED, () -> unanswered.remove(id));
            }
        });
    }

    /** Act on a message a peer sent; one that is not for this backend is dropped. */
    public void onMessage(RemoteTeleport message) {
        Objects.requireNonNull(message, "message");
        switch (message) {
            case RemoteTeleport.StaffMove staff -> onMove(staff);
            case RemoteTeleport.Answer answer -> onAnswer(answer);
            case RemoteTeleport.Request request -> {
                // A teleport request is handled by CrossServerRequests.
            }
        }
    }

    private void onMove(RemoteTeleport.StaffMove move) {
        if (!move.subjectServer().equals(network.localServer()) || !actedOn.add(move.id())) {
            return;
        }
        later(REMEMBERED, () -> actedOn.remove(move.id()));
        PlayerRef subject = move.subject();
        // A subject hidden from the staff member reads exactly like one who is not here.
        if (!players.isOnline(subject.uuid()) || visibility.hides(subject, move.actor(), move.seeLevel())) {
            answer(move, Step.SUBJECT_OFFLINE, subject, "");
            return;
        }
        Optional<OnlinePlayer> destination = presence.find(move.destination().uuid())
                .filter(where -> isHere(where) || presence.isLive(where.server()));
        if (destination.isEmpty()) {
            answer(move, Step.DESTINATION_OFFLINE, move.destination(), "");
            return;
        }
        moveFromHere(
                move.actor(),
                move.seeLevel(),
                subject,
                destination.get(),
                () -> answer(move, Step.MOVED, subject, ""),
                server -> answer(move, Step.SENT, subject, server),
                () -> answer(move, Step.REFUSED, subject, ""),
                () -> answer(move, Step.SUBJECT_OFFLINE, subject, ""),
                () -> answer(move, Step.DESTINATION_OFFLINE, move.destination(), ""));
    }

    private void onAnswer(RemoteTeleport.Answer answer) {
        if (!answer.toServer().equals(network.localServer()) || !isStaffStep(answer.step())) {
            return;
        }
        Pending sent = pending.remove(answer.id());
        if (sent == null) {
            sent = unanswered.remove(answer.id());
        }
        if (sent == null) {
            return;
        }
        PlayerRef actor = sent.actor();
        switch (answer.step()) {
            case MOVED -> notifier.send(actor, TeleportMessageKey.TP_DONE);
            case SENT ->
                notifier.send(
                        actor,
                        TeleportMessageKey.TP_CROSS_SERVER_SENT,
                        sentLine(sent.subject(), sent.destination(), answer.detail()));
            case REFUSED ->
                notifier.send(
                        actor,
                        TeleportMessageKey.TP_CROSS_SERVER_REFUSED,
                        Map.of("player", sent.subject().name()));
            case SUBJECT_OFFLINE -> offline(actor, sent.subject().name());
            case DESTINATION_OFFLINE -> offline(actor, sent.destination().name());
            default -> {
                // Not a staff step; filtered out above.
            }
        }
    }

    /**
     * Move {@code mover}, a player on this backend, to {@code destination} for {@code actor}: in place when the
     * destination is here too, through the hop otherwise. A destination hidden from the actor at {@code seeLevel} moves
     * nobody: here by this backend's vanish state, elsewhere by its shared vanish row, read before any connect. Exactly
     * one of the outcomes runs.
     */
    private void moveFromHere(
            PlayerRef actor,
            int seeLevel,
            PlayerRef mover,
            OnlinePlayer destination,
            Runnable moved,
            java.util.function.Consumer<String> sent,
            Runnable refused,
            Runnable gone,
            Runnable destinationHidden) {
        PlayerRef anchor = destination.player();
        if (isHere(destination)) {
            if (visibility.hides(anchor, actor, seeLevel)) {
                destinationHidden.run();
                return;
            }
            moveLocally(mover, anchor, moved, refused);
            return;
        }
        String server = destination.server();
        offTick(() -> {
            if (!players.isOnline(mover.uuid())) {
                gone.run();
                return;
            }
            if (visibility.hiddenElsewhere(anchor, seeLevel)) {
                destinationHidden.run();
                return;
            }
            if (!hops.admit(mover, HopKind.PLAYER_STAFF, anchor, server, seeLevel)) {
                refused.run();
                return;
            }
            hops.send(mover, HopKind.PLAYER_STAFF, anchor, server, seeLevel);
            sent.accept(server);
        });
    }

    /** A local staff teleport: the destination's position is read on their thread, as {@code /tp} does here. */
    private void moveLocally(PlayerRef mover, PlayerRef anchor, Runnable moved, Runnable refused) {
        try {
            scheduler.onEntity(
                    anchor,
                    () -> {
                        Optional<Position> standing = locator.locate(anchor);
                        if (standing.isEmpty()) {
                            refused.run();
                            return;
                        }
                        executor.teleport(mover, Destination.at(standing.get()), TeleportKind.ADMIN, moved, refused);
                    },
                    refused);
        } catch (RuntimeException unscheduled) {
            refused.run();
        }
    }

    private Optional<OnlinePlayer> locate(Named named) {
        return presence.findByName(named.name())
                .filter(where -> named.here() ? isHere(where) : !isHere(where) && presence.isLive(where.server()));
    }

    private boolean isHere(OnlinePlayer where) {
        return where.server().equals(network.localServer());
    }

    private void offline(PlayerRef actor, String name) {
        notifier.send(actor, TeleportError.TARGET_OFFLINE.messageKey(), Map.of("player", name));
    }

    private static Map<String, String> sentLine(PlayerRef subject, PlayerRef destination, String server) {
        return Map.of("player", subject.name(), "target", destination.name(), "server", server);
    }

    private static boolean isStaffStep(Step step) {
        return switch (step) {
            case MOVED, SENT, REFUSED, SUBJECT_OFFLINE, DESTINATION_OFFLINE -> true;
            default -> false;
        };
    }

    private void answer(RemoteTeleport.StaffMove move, Step step, PlayerRef about, String detail) {
        network.send(
                new RemoteTeleport.Answer(network.localServer(), move.id(), step, move.fromServer(), about, detail));
    }

    private void later(Duration delay, Runnable task) {
        try {
            scheduler.asyncAfter(delay, task);
        } catch (RuntimeException refused) {
            // A stopping backend drops its timers; only a no-answer line or a dedupe entry is lost with them.
            log.warn("event=cross_server_tp_timer_refused reason={}", refused.toString());
        }
    }

    private void offTick(Runnable work) {
        try {
            scheduler.async(work);
        } catch (RuntimeException refused) {
            // Nothing has moved or been spent yet, so a stopping backend may drop the teleport.
            log.warn("event=cross_server_tp_async_refused reason={}", refused.toString());
        }
    }
}
