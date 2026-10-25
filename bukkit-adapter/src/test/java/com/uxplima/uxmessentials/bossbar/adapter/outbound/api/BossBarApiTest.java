package com.uxplima.uxmessentials.bossbar.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.bossbar.adapter.outbound.BossBarAnnouncements;
import com.uxplima.uxmessentials.bossbar.adapter.outbound.BossBarRenderer;
import com.uxplima.uxmessentials.bossbar.application.ToggleBossBars;
import com.uxplima.uxmessentials.bossbar.application.port.BossBarVisibilityStore;
import com.uxplima.uxmessentials.bossbar.domain.BossBarConfig;
import com.uxplima.uxmessentials.bossbar.domain.BossBarDefinition;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** What another plugin reads and does through the published boss bar query and actions. */
class BossBarApiTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    private ServerMock server;
    private PlayerMock player;
    private final Map<UUID, Boolean> hidden = new HashMap<>();
    private final BossBarAnnouncements announcements = new BossBarAnnouncements();
    private final AtomicInteger redraws = new AtomicInteger();
    private final BossBarConfig config = new BossBarConfig(List.of(
            bar("website", false),
            new BossBarDefinition(
                    "drop", "Drop", "YELLOW", "PROGRESS", "time", DisplayCondition.always(), Optional.empty(), true)));
    private BossBarRenderer renderer;
    private BossBarActions actions;
    private BossBarQueries queries;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        BossBarVisibilityStore store = new BossBarVisibilityStore() {
            @Override
            public boolean hidden(PlayerRef who) {
                return hidden.getOrDefault(who.uuid(), false);
            }

            @Override
            public void setHidden(PlayerRef who, boolean value) {
                hidden.put(who.uuid(), value);
            }
        };
        renderer = new BossBarRenderer(
                () -> config,
                store,
                new AnimationRegistry(List.of()),
                announcements,
                (v, left) -> left.toMinutes() + "m",
                () -> NOW);
        ToggleBossBars toggle = new ToggleBossBars(
                store, new Notifier((viewer, key, values) -> key.key(), (viewer, text) -> {}), event -> {});
        PlayerLookup lookup = new PlayerLookup() {
            @Override
            public Optional<PlayerRef> findOnlineByName(String name) {
                return Optional.empty();
            }

            @Override
            public Optional<PlayerRef> findByUuid(UUID uuid) {
                return Optional.empty();
            }

            @Override
            public boolean isOnline(UUID uuid) {
                return server.getPlayer(uuid) != null;
            }
        };
        actions = new BossBarActions(
                toggle,
                store,
                renderer,
                announcements,
                () -> config,
                redraws::incrementAndGet,
                () -> NOW,
                lookup,
                new Inline());
        queries = new BossBarQueries(() -> config, store, renderer, lookup, new Inline());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theQueryListsTheWrittenBarsAndWhatAPlayerSees() {
        renderer.renderFor(player);

        assertThat(queries.bars()).containsExactly("website", "drop");
        assertThat(queries.shown(player.getUniqueId()).join()).containsExactly("website");
        assertThat(queries.hidden(player.getUniqueId()).join()).contains(false);
        assertThat(queries.hidden(UUID.randomUUID()).join()).isEmpty();
    }

    @Test
    void hidingTwiceIsRefusedAsAlreadyInThatState() {
        assertThat(actions.hide(player.getUniqueId()).join().succeeded()).isTrue();
        assertThat(player.activeBossBars()).isEmpty();

        assertThat(code(actions.hide(player.getUniqueId()).join())).isEqualTo(UxmFailure.ALREADY_IN_STATE);
        assertThat(actions.show(player.getUniqueId()).join().succeeded()).isTrue();
        assertThat(player.activeBossBars()).hasSize(1);
    }

    @Test
    void anAnnouncementPutsTheBarUpForEverybodyAndCountsIt() {
        assertThat(actions.announce("DROP", Duration.ofMinutes(5)).join().succeeded())
                .isTrue();

        assertThat(redraws).hasValue(1);
        assertThat(queries.remaining(player.getUniqueId(), "drop").join()).contains(Duration.ofMinutes(5));
    }

    @Test
    void anUnknownBarOrANonPositiveLengthIsRefused() {
        assertThat(code(actions.announce("nothing", Duration.ofMinutes(5)).join()))
                .isEqualTo(UxmFailure.NOT_FOUND);
        assertThat(code(actions.announce("drop", Duration.ZERO).join())).isEqualTo(UxmFailure.REFUSED);
        assertThat(redraws).hasValue(0);
    }

    @Test
    void aBarSentToOnePlayerIsDrawnForThemAndRefusedForSomebodyAway() {
        assertThat(actions.send(player.getUniqueId(), "drop", Duration.ofMinutes(2))
                        .join()
                        .succeeded())
                .isTrue();
        assertThat(queries.shown(player.getUniqueId()).join()).containsExactly("website", "drop");

        assertThat(code(actions.send(UUID.randomUUID(), "drop", Duration.ofMinutes(2))
                        .join()))
                .isEqualTo(UxmFailure.PLAYER_OFFLINE);
    }

    private static String code(UxmOutcome outcome) {
        return outcome.failureOrThrow().code();
    }

    private static BossBarDefinition bar(String id, boolean announcementOnly) {
        return new BossBarDefinition(
                id, id, "GREEN", "PROGRESS", "100", DisplayCondition.always(), Optional.empty(), announcementOnly);
    }

    private static final class Inline implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
