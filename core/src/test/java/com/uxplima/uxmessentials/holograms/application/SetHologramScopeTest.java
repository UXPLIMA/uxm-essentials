package com.uxplima.uxmessentials.holograms.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.application.port.LinkedNpcLocator;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.domain.LocalServer;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import com.uxplima.uxmessentials.testing.Fixtures;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/** A hologram shows only on the servers its scope names; with no scope it shows on all of them. */
class SetHologramScopeTest {

    private static final HologramName BOARD = HologramName.of("board");
    private static final LocalServer LOBBY = new LocalServer("lobby", List.of("hubs"));

    private final Rows rows = new Rows();
    private final RecordingView drawn = new RecordingView();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final Npcs npcs = new Npcs();
    private final HologramView view = new ScopedHologramView(drawn, LOBBY, npcs);
    private final SetHologramScope scope = new SetHologramScope(rows, view, notifier.notifier(), LOBBY);

    private static Hologram board() {
        return Hologram.create(BOARD, Fixtures.at(0, 70, 0), List.of(new HologramLine("Welcome")), NOW);
    }

    @Test
    void aHologramWithNoScopeShowsOnEveryServer() {
        view.render(board());
        new ScopedHologramView(drawn, new LocalServer("survival", List.of()), npcs).render(board());

        assertThat(drawn.rendered).containsExactly("board", "board");
        assertThat(drawn.despawned).isEmpty();
    }

    @Test
    void aHologramScopedToAnotherServerIsNotDrawnHere() {
        view.render(board().withScope(ServerScope.server("survival")));

        assertThat(drawn.rendered).isEmpty();
        assertThat(drawn.despawned).containsExactly("board");
    }

    @Test
    void aHologramScopedToASetThisServerIsInIsDrawn() {
        view.render(board().withScope(ServerScope.set("hubs")));

        assertThat(drawn.rendered).containsExactly("board");
    }

    @Test
    void aHologramRidingAnNpcScopedAwayFromThisServerIsNotDrawnHere() {
        npcs.scopedAway.add("guide");

        view.render(board().linkedTo("guide"));
        view.render(board().linkedTo("greeter"));

        assertThat(drawn.despawned).containsExactly("board");
        assertThat(drawn.rendered).containsExactly("board");
    }

    @Test
    void scopingAHologramAwaySavesItAndTakesItOffHere() {
        rows.save(board());

        assertThat(scope.set(ALEX, BOARD, "server:survival").isOk()).isTrue();

        assertThat(rows.find(BOARD).orElseThrow().scope()).isEqualTo(ServerScope.server("survival"));
        assertThat(drawn.despawned).containsExactly("board");
        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(
                        ALEX, "hologram.scope.set", Map.of("name", "board", "scope", "server:survival")));
    }

    @Test
    void clearingTheScopeShowsTheHologramHereAgain() {
        rows.save(board().withScope(ServerScope.server("survival")));

        scope.set(ALEX, BOARD, "clear");

        assertThat(rows.find(BOARD).orElseThrow().scope()).isNull();
        assertThat(drawn.rendered).containsExactly("board");
        assertThat(notifier.keys()).containsExactly("hologram.scope.cleared");
    }

    @Test
    void aWordThatNamesNoScopeChangesNothing() {
        rows.save(board());

        assertThat(scope.set(ALEX, BOARD, "minigames").asError()).contains(HologramError.INVALID_SCOPE);

        assertThat(rows.find(BOARD).orElseThrow().scope()).isNull();
        assertThat(drawn.rendered).isEmpty();
        assertThat(notifier.keys()).containsExactly("hologram.scope.invalid");
    }

    /** The NPCs a hologram can ride; every NPC shows here unless the test scoped it away. */
    private static final class Npcs implements LinkedNpcLocator {
        private final Set<String> scopedAway = new java.util.HashSet<>();

        @Override
        public Optional<Position> locate(String npcName) {
            return Optional.of(Fixtures.at(5, 70, 5));
        }

        @Override
        public boolean shownHere(String npcName) {
            return !scopedAway.contains(npcName);
        }
    }

    /** Hologram rows in memory, the durable store the use case writes through. */
    private static final class Rows implements HologramRepository {
        private final Map<String, Hologram> rows = new LinkedHashMap<>();

        @Override
        public Optional<Hologram> find(HologramName name) {
            return Optional.ofNullable(rows.get(name.value()));
        }

        @Override
        public List<Hologram> all() {
            return List.copyOf(rows.values());
        }

        @Override
        public boolean exists(HologramName name) {
            return rows.containsKey(name.value());
        }

        @Override
        public void save(Hologram hologram) {
            rows.put(hologram.name().value(), hologram);
        }

        @Override
        public void delete(HologramName name) {
            rows.remove(name.value());
        }

        @Override
        public Set<UUID> manualViewers(HologramName name) {
            return Set.of();
        }

        @Override
        public void showTo(HologramName name, UUID viewer) {}

        @Override
        public void hideFrom(HologramName name, UUID viewer) {}

        @Override
        public Set<UUID> blacklisted(HologramName name) {
            return Set.of();
        }

        @Override
        public void addToBlacklist(HologramName name, UUID viewer) {}

        @Override
        public void removeFromBlacklist(HologramName name, UUID viewer) {}
    }

    /** The in-world display, recording what was drawn and taken off. */
    private static final class RecordingView implements HologramView {
        private final List<String> rendered = new ArrayList<>();
        private final List<String> despawned = new ArrayList<>();

        @Override
        public void render(Hologram hologram) {
            rendered.add(hologram.name().value());
        }

        @Override
        public void despawn(HologramName name) {
            despawned.add(name.value());
        }

        @Override
        public void applyManualViewer(HologramName name, UUID viewer, boolean visible) {}
    }
}
