package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.shared.network.StaffActionFrame.Action;
import org.junit.jupiter.api.Test;

/**
 * Staff on one backend act on a player connected to another: the action reaches the backend it names, once, and no
 * other; a notice reaches every backend.
 */
class StaffActionsTest {

    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");

    private final List<NetworkMessage> wire = new ArrayList<>();
    private final RemoteSyncRegistry lobby = new RemoteSyncRegistry();
    private final RemoteSyncRegistry creative = new RemoteSyncRegistry();

    @Test
    void aKickIssuedOnSurvivalClosesTheConnectionOnTheBackendHoldingThePlayerOnce() {
        List<String> kickedOnLobby = new ArrayList<>();
        List<String> kickedOnCreative = new ArrayList<>();
        lobby.register(ModerationSync.kickListener(
                "lobby", (target, reason) -> kickedOnLobby.add(target.name() + ":" + reason.orElse("-"))));
        creative.register(
                ModerationSync.kickListener("creative", (target, reason) -> kickedOnCreative.add(target.name())));
        SanctionSync survival = ModerationSync.publisher(new Wire("survival"));

        survival.kickElsewhere(STEVE, "lobby", Optional.of("spam"));
        deliver();
        deliver();

        assertThat(kickedOnLobby).containsExactly("Steve:spam");
        assertThat(kickedOnCreative).isEmpty();
    }

    @Test
    void aKickWithNoReasonArrivesWithNone() {
        List<Optional<String>> reasons = new ArrayList<>();
        lobby.register(ModerationSync.kickListener("lobby", (target, reason) -> reasons.add(reason)));

        ModerationSync.publisher(new Wire("survival")).kickElsewhere(STEVE, "lobby", Optional.empty());
        deliver();

        assertThat(reasons).containsExactly(Optional.empty());
    }

    @Test
    void aListenerTakesOnlyItsOwnAction() {
        List<Action> seen = new ArrayList<>();
        lobby.register(StaffActions.listener("lobby", Action.STAFF_MODE, frame -> seen.add(Action.STAFF_MODE)));

        new StaffActions(new Wire("survival")).send(Action.SECURITY_FORCE, "lobby", STEVE, "Staff", Map.of());
        new StaffActions(new Wire("survival")).send(Action.STAFF_MODE, "lobby", STEVE, "Staff", Map.of());
        deliver();

        assertThat(seen).containsExactly(Action.STAFF_MODE);
    }

    @Test
    void aNoticeReachesEveryBackend() {
        List<String> shown = new ArrayList<>();
        lobby.register(StaffActions.listener(
                "lobby",
                Action.STAFF_NOTICE,
                frame -> shown.add("lobby " + frame.values().get("player"))));
        creative.register(StaffActions.listener(
                "creative",
                Action.STAFF_NOTICE,
                frame -> shown.add("creative " + frame.values().get("player"))));

        new StaffActions(new Wire("survival")).notice("staff.alert.enter", Map.of("player", "Alex"));
        deliver();

        assertThat(shown).containsExactlyInAnyOrder("lobby Alex", "creative Alex");
    }

    /** Hands every frame on the wire to both backends, encoded and decoded as the transport would. */
    private void deliver() {
        for (NetworkMessage message : wire) {
            NetworkMessage read = NetworkMessageCodec.decode(NetworkMessageCodec.encode(message));
            for (RemoteSyncRegistry backend : List.of(lobby, creative)) {
                backend.dispatch(read, (listener, failure) -> {
                    throw new AssertionError(failure);
                });
            }
        }
    }

    private final class Wire implements BusPublisher {

        private final String serverId;

        Wire(String serverId) {
            this.serverId = serverId;
        }

        @Override
        public void publish(NetworkMessage message) {
            wire.add(message);
        }

        @Override
        public String serverId() {
            return serverId;
        }
    }
}
