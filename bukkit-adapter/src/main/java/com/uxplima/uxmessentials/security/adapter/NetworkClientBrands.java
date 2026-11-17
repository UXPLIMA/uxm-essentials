package com.uxplima.uxmessentials.security.adapter;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.RemoteSyncListener;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.StaffActions;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.StaffActionFrame;
import com.uxplima.uxmessentials.shared.network.StaffActionFrame.Action;
import org.jspecify.annotations.NullMarked;

/**
 * The client brand a player reported, wherever on the network they are connected. A brand arrives on the connection
 * and is kept only by the backend holding it, so for a player on another backend that backend is asked and answers
 * over the bus. A backend that does not answer in time (stopping, or the player just left) reads as no brand.
 */
@NullMarked
public final class NetworkClientBrands {

    /** How long the other backend has to answer; a staff read, so a short wait is plenty. */
    static final Duration WAIT = Duration.ofSeconds(3);

    private static final String ASK = "ask";
    private static final String BRAND = "brand";

    private final ClientBrandRegistry local;
    private final NetworkPresence presence;
    private final StaffActions actions;
    private final Scheduler scheduler;
    private final ConcurrentMap<UUID, Question> waiting = new ConcurrentHashMap<>();

    public NetworkClientBrands(
            ClientBrandRegistry local, NetworkPresence presence, StaffActions actions, Scheduler scheduler) {
        this.local = Objects.requireNonNull(local, "local");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** The brand {@code target} reported, or empty when they reported none or their backend did not answer. */
    public CompletableFuture<Optional<String>> brandOf(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        String here = presence.localServer();
        Optional<String> elsewhere =
                presence.find(target.uuid()).map(OnlinePlayer::server).filter(server -> !server.equals(here));
        if (elsewhere.isEmpty()) {
            return CompletableFuture.completedFuture(local.brandOf(target.uuid()));
        }
        UUID ask = UUID.randomUUID();
        CompletableFuture<Optional<String>> answer = new CompletableFuture<>();
        waiting.put(ask, new Question(elsewhere.get(), target.uuid(), answer));
        var unused = answer.whenComplete((done, failure) -> waiting.remove(ask));
        try {
            scheduler.asyncAfter(WAIT, () -> answer.complete(Optional.empty()));
            actions.send(Action.CLIENT_BRAND_ASK, elsewhere.get(), target, "", Map.of(ASK, ask.toString()));
        } catch (RuntimeException refused) {
            // The scheduler or the bus refuses work while the plugin stops.
            answer.complete(Optional.empty());
        }
        return answer;
    }

    /** The listeners that answer another backend's question and take the answers to this one's. */
    public RemoteSyncListener[] listeners() {
        String here = actions.serverId();
        return new RemoteSyncListener[] {
            StaffActions.listener(here, Action.CLIENT_BRAND_ASK, this::answer),
            StaffActions.listener(here, Action.CLIENT_BRAND_ANSWER, this::answered)
        };
    }

    private void answer(StaffActionFrame frame) {
        Map<String, String> values = new HashMap<>();
        values.put(ASK, frame.values().getOrDefault(ASK, ""));
        local.brandOf(frame.target()).ifPresent(brand -> values.put(BRAND, brand));
        actions.send(
                Action.CLIENT_BRAND_ANSWER,
                frame.originServer(),
                new PlayerRef(frame.target(), frame.targetName()),
                "",
                values);
    }

    private void answered(StaffActionFrame frame) {
        Question question = askOf(frame).map(waiting::get).orElse(null);
        // An answer counts only from the backend asked and about the player asked about.
        if (question != null
                && question.server().equals(frame.originServer())
                && question.player().equals(frame.target())) {
            question.answer().complete(Optional.ofNullable(frame.values().get(BRAND)));
        }
    }

    private static Optional<UUID> askOf(StaffActionFrame frame) {
        try {
            return Optional.of(UUID.fromString(frame.values().getOrDefault(ASK, "")));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private record Question(String server, UUID player, CompletableFuture<Optional<String>> answer) {}
}
