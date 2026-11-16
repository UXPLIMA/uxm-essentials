package com.uxplima.uxmessentials.nametags.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.nametags.domain.NametagAppearance;
import com.uxplima.uxmessentials.nametags.domain.NametagConfig;
import com.uxplima.uxmessentials.nametags.domain.NametagFormat;
import com.uxplima.uxmessentials.nametags.domain.NametagVisibility;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ReaderText;
import com.uxplima.uxmessentials.shared.adapter.outbound.team.PlayerTeamCoordinator;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmlib.nametag.Appearance;
import com.uxplima.uxmlib.nametag.NametagPackets;
import com.uxplima.uxmlib.nametag.NametagRenderer;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PacketNametagPresenterTest {

    private ServerMock server;
    private Player tracking;
    private Player hidden;
    private PacketNametagPresenter presenter;

    @BeforeEach
    void mock() {
        server = MockBukkit.mock();
        tracking = join("Tracking", Set.of());
        hidden = join("Hidden", Set.of());
        presenter = new PacketNametagPresenter(
                () -> new NametagConfig(List.of()),
                new NametagRenderer(new NoPackets(), new PaperScheduler(MockBukkit.createMockPlugin())),
                new AnimationRegistry(List.of()),
                (viewer, wearer) -> !viewer.uuid().equals(hidden.getUniqueId()),
                () -> Duration.ofSeconds(1),
                new PlayerTeamCoordinator(),
                () -> true,
                new ReaderText(
                        line -> false,
                        (reader, line) -> line,
                        player -> ReaderText.Affix.NONE,
                        (reader, about) -> UnaryOperator.identity()));
    }

    @AfterEach
    void unmock() {
        MockBukkit.unmock();
    }

    @Test
    void onlyClientsThatTrackTheWearerAreSentTheNametag() {
        // A client that does not track the wearer cannot seat the lines on them, so they would stay at its feet.
        Player wearer = join("Wearer", Set.of(tracking));

        assertThat(presenter.eligibleViewers(wearer, format(false))).containsExactly(tracking);
    }

    @Test
    void aTrackingViewerTheWearerIsVanishedFromIsLeftOut() {
        Player wearer = join("Wearer", Set.of(tracking, hidden));

        assertThat(presenter.eligibleViewers(wearer, format(true))).containsExactly(tracking);
    }

    private static NametagFormat format(boolean respectVanish) {
        return new NametagFormat(
                "default",
                DisplayCondition.always(),
                0,
                List.of("{player}"),
                NametagAppearance.defaults(),
                new NametagVisibility(DisplayCondition.always(), false, respectVanish, OptionalDouble.of(0)));
    }

    private Player join(String name, Set<Player> trackedBy) {
        TrackedPlayer player = new TrackedPlayer(server, name, trackedBy);
        server.addPlayer(player);
        return player;
    }

    /** A player whose watchers are given, which MockBukkit does not track on its own. */
    private static final class TrackedPlayer extends PlayerMock {

        private final Set<Player> trackedBy;

        TrackedPlayer(ServerMock server, String name, Set<Player> trackedBy) {
            super(server, name, UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
            this.trackedBy = Set.copyOf(trackedBy);
        }

        @Override
        public Set<Player> getTrackedBy() {
            return trackedBy;
        }
    }

    /** Packets are never built while viewers are chosen. */
    private static final class NoPackets implements NametagPackets {

        @Override
        public int allocateEntityId() {
            throw new UnsupportedOperationException("allocateEntityId");
        }

        @Override
        public Object spawnPacket(int entityId, double x, double y, double z) {
            throw new UnsupportedOperationException("spawnPacket");
        }

        @Override
        public Object metadataPacket(
                int entityId, Component text, Appearance appearance, int lineWidth, Vector3f translation) {
            throw new UnsupportedOperationException("metadataPacket");
        }

        @Override
        public Object mountPacket(int vehicleId, int[] passengerIds) {
            throw new UnsupportedOperationException("mountPacket");
        }

        @Override
        public Object removePacket(int[] entityIds) {
            throw new UnsupportedOperationException("removePacket");
        }

        @Override
        public Object bundle(List<Object> packets) {
            throw new UnsupportedOperationException("bundle");
        }

        @Override
        public void send(Player viewer, Object packet) {
            throw new UnsupportedOperationException("send");
        }
    }
}
