package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.RemoteSyncListener;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.StoredPermissions.Answer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.PermissionQueryFrame;
import org.jspecify.annotations.NullMarked;

/**
 * Reads a node for a player connected to another backend by asking that backend over the bus, which reads it live on
 * the player's own thread there. Both halves live here: {@link #ask} sends the question and waits for its answer, and
 * the listener answers the questions other backends address to this one.
 *
 * <p>A question that gets no answer within {@link #WAIT} (no carrier reaches that backend, or it runs a release that
 * does not know the question) answers {@link Answer#NO_SOURCE}: nothing here could read the player. The frames are
 * signed with {@code network.bus.secret} like every other frame on the proxy carrier, so a client cannot forge an
 * answer.
 */
@NullMarked
public final class BusPermissionReads implements RemoteSyncListener {

    /** How long the other backend has to answer; shorter than the sanction's own wait, so this answer is the one used. */
    static final Duration WAIT = Duration.ofSeconds(3);

    private final BusPublisher bus;
    private final Permissions live;
    private final PlayerLookup localPlayers;
    private final Scheduler scheduler;
    private final Logger log;
    private final ConcurrentMap<UUID, Question> waiting = new ConcurrentHashMap<>();

    public BusPermissionReads(
            BusPublisher bus, Permissions live, PlayerLookup localPlayers, Scheduler scheduler, Logger log) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.live = Objects.requireNonNull(live, "live");
        this.localPlayers = Objects.requireNonNull(localPlayers, "localPlayers");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Ask {@code server} whether {@code who}, connected there, holds {@code node}. Never completes exceptionally. */
    public CompletableFuture<Answer> ask(PlayerRef who, String node, String server) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(server, "server");
        UUID query = UUID.randomUUID();
        CompletableFuture<Answer> answer = new CompletableFuture<>();
        waiting.put(query, new Question(server, who.uuid(), node, answer));
        var unused = answer.whenComplete((done, failure) -> waiting.remove(query));
        try {
            scheduler.asyncAfter(WAIT, () -> {
                if (answer.complete(Answer.NO_SOURCE)) {
                    log.warn("event=permission_query_unanswered player={} server={}", who.name(), server);
                }
            });
            bus.publish(new PermissionQueryFrame(
                    bus.serverId(), query, server, who.uuid(), node, PermissionQueryFrame.ASK));
        } catch (RuntimeException refused) {
            // The scheduler or the bus refuses work while the plugin stops.
            answer.complete(Answer.NO_SOURCE);
        }
        return answer;
    }

    @Override
    public void onRemoteChange(NetworkMessage message) {
        if (!(message instanceof PermissionQueryFrame frame)
                || !frame.toServer().equals(bus.serverId())) {
            return;
        }
        if (frame.step().equals(PermissionQueryFrame.ASK)) {
            answer(frame);
            return;
        }
        Question question = waiting.get(frame.queryId());
        if (question == null || !question.answeredBy(frame)) {
            return;
        }
        CompletableFuture<Answer> asked = question.answer();
        switch (frame.step()) {
            case PermissionQueryFrame.HELD -> asked.complete(Answer.HELD);
            case PermissionQueryFrame.NOT_HELD -> asked.complete(Answer.NOT_HELD);
            case PermissionQueryFrame.NO_ANSWER -> asked.complete(Answer.NO_ANSWER);
            default -> {
                // A step from a newer peer: leave the question to its time limit.
            }
        }
    }

    /** Read the node on the player's own thread and send the answer back; a player gone from here gets no answer. */
    private void answer(PlayerRef who, PermissionQueryFrame frame) {
        try {
            scheduler.onEntity(
                    who,
                    () -> reply(frame, step(live.read(who, frame.node()))),
                    () -> reply(frame, PermissionQueryFrame.NO_ANSWER));
        } catch (RuntimeException refused) {
            reply(frame, PermissionQueryFrame.NO_ANSWER);
        }
    }

    private void answer(PermissionQueryFrame frame) {
        Optional<PlayerRef> here =
                localPlayers.isOnline(frame.player()) ? localPlayers.findByUuid(frame.player()) : Optional.empty();
        here.ifPresentOrElse(who -> answer(who, frame), () -> reply(frame, PermissionQueryFrame.NO_ANSWER));
    }

    private void reply(PermissionQueryFrame question, String step) {
        bus.publish(new PermissionQueryFrame(
                bus.serverId(), question.queryId(), question.originServer(), question.player(), question.node(), step));
    }

    /**
     * One question waiting for its answer. An answer counts only from the backend asked, about the player and the node
     * asked, so a frame that reuses a question's id cannot answer another question.
     */
    private record Question(String server, UUID player, String node, CompletableFuture<Answer> answer) {

        boolean answeredBy(PermissionQueryFrame frame) {
            return frame.originServer().equals(server)
                    && frame.player().equals(player)
                    && frame.node().equals(node);
        }
    }

    private static String step(Optional<Boolean> read) {
        return read.map(held -> held ? PermissionQueryFrame.HELD : PermissionQueryFrame.NOT_HELD)
                .orElse(PermissionQueryFrame.NO_ANSWER);
    }
}
