package com.uxplima.uxmessentials.scoreboard.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderer;
import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.domain.DisplayContent;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarBoard;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarConfig;
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
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Proves the event-driven refresh: a world change re-renders the player immediately so a world-blacklist takes effect
 * at once instead of lagging a render tick. The player starts in a non-blacklisted world (board shows), moves into a
 * blacklisted world, and the world-change handler (hopping through a synchronous {@code Scheduler} stub) tears the
 * board down. The game-mode handler runs the same re-render path and must not throw.
 */
class ScoreboardConnectionListenerTest {

    private ServerMock server;
    private com.uxplima.uxmlib.hud.scoreboard.SidebarManager sidebars;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        sidebars = new com.uxplima.uxmlib.hud.scoreboard.SidebarManager(server.getScoreboardManager());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aWorldChangeIntoABlacklistedWorldTearsDownTheBoard() {
        PlayerMock player = server.addPlayer();
        World origin = player.getWorld();
        WorldMock blacklisted = server.addSimpleWorld("world_blacklisted");
        // Start with nothing blacklisted so the board paints, then move into a world the live content blacklists.
        AtomicReference<SidebarConfig> live = new AtomicReference<>(config(Set.of()));
        ScoreboardRenderer renderer = new ScoreboardRenderer(sidebars, alwaysShown(), live::get);

        renderer.renderFor(player);
        assertThat(sidebars.count()).isEqualTo(1);

        player.teleport(blacklisted.getSpawnLocation());
        live.set(config(Set.of(blacklisted.getName())));
        ScoreboardConnectionListener listener = new ScoreboardConnectionListener(renderer, new SyncScheduler());
        listener.onWorldChange(new PlayerChangedWorldEvent(player, origin));

        assertThat(sidebars.count()).isZero();
    }

    @Test
    void aGameModeChangeReRendersWithoutThrowing() {
        PlayerMock player = server.addPlayer();
        ScoreboardRenderer renderer = renderer(Set.of());
        ScoreboardConnectionListener listener = new ScoreboardConnectionListener(renderer, new SyncScheduler());

        PlayerGameModeChangeEvent event = new PlayerGameModeChangeEvent(
                player, GameMode.CREATIVE, PlayerGameModeChangeEvent.Cause.PLUGIN, Component.empty());
        assertThatCode(() -> listener.onGameModeChange(event)).doesNotThrowAnyException();
        assertThat(sidebars.count()).isEqualTo(1);
    }

    private ScoreboardRenderer renderer(Set<String> blacklist) {
        AtomicReference<SidebarConfig> ref = new AtomicReference<>(config(blacklist));
        return new ScoreboardRenderer(sidebars, alwaysShown(), ref::get);
    }

    private static SidebarConfig config(Set<String> blacklist) {
        return new SidebarConfig(
                List.of(new SidebarBoard("default", content(blacklist), DisplayCondition.always(), 0)));
    }

    private static DisplayContent content(Set<String> blacklist) {
        // Keep the score numbers shown: MockBukkit leaves Objective.numberFormat unimplemented, so a board that hid
        // them could not render to completion here. The re-render wiring under test is independent of that flag.
        return new DisplayContent(
                Optional.of("<gold>Server"), List.of("<white>line"), false, Duration.ofSeconds(1L), blacklist);
    }

    private static ScoreboardVisibilityStore alwaysShown() {
        return new ScoreboardVisibilityStore() {
            @Override
            public boolean hidden(PlayerRef who) {
                return false;
            }

            @Override
            public boolean toggle(PlayerRef who) {
                return false;
            }

            @Override
            public void forget(PlayerRef who) {}
        };
    }

    private static final class SyncScheduler implements Scheduler {
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
