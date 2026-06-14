package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.domain.DisplayContent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the previously untested {@link ScoreboardRenderer} render path under MockBukkit: authored content shows a
 * sidebar, a blacklisted world tears it down, and the hide-score-numbers path reaches the objective's number-format
 * setter.
 *
 * <p>MockBukkit's {@code ObjectiveMock.numberFormat} is unimplemented (it throws), so a board that hides its numbers
 * cannot be rendered to completion here; {@link #appliesABlankNumberFormatWhenHidingNumbers()} asserts the call lands
 * on that setter — proving the production wiring invokes the real Paper API — and the other render cases keep numbers
 * shown so they exercise the rest of the path. On real Paper 1.21.11 the setter applies {@code NumberFormat.blank()}.
 */
class ScoreboardRendererTest {

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
    void rendersASidebarForAuthoredContent() {
        PlayerMock player = server.addPlayer();
        ScoreboardRenderer renderer = renderer(content(false, Set.of()));

        assertThatCode(() -> renderer.renderFor(player)).doesNotThrowAnyException();

        assertThat(sidebars.count()).isEqualTo(1);
    }

    @Test
    void appliesABlankNumberFormatWhenHidingNumbers() {
        PlayerMock player = server.addPlayer();
        ScoreboardRenderer renderer = renderer(content(true, Set.of()));

        // The renderer reaches Objective.numberFormat(blank()); MockBukkit leaves that setter unimplemented, so the
        // call surfaces here. On real Paper the same call hides the red side numbers.
        assertThatThrownBy(() -> renderer.renderFor(player))
                .isInstanceOf(org.mockbukkit.mockbukkit.exception.UnimplementedOperationException.class)
                .hasStackTraceContaining("numberFormat");
    }

    @Test
    void tearsDownTheBoardInABlacklistedWorld() {
        PlayerMock player = server.addPlayer();
        String world = player.getWorld().getName();
        ScoreboardRenderer renderer = renderer(content(false, Set.of(world)));

        renderer.renderFor(player);

        assertThat(sidebars.count()).isZero();
    }

    private ScoreboardRenderer renderer(DisplayContent content) {
        AtomicReference<DisplayContent> ref = new AtomicReference<>(content);
        return new ScoreboardRenderer(sidebars, alwaysShown(), ref::get);
    }

    private static DisplayContent content(boolean hideScoreNumbers, Set<String> blacklist) {
        return new DisplayContent(
                Optional.of("<gold>Server"),
                List.of("<white>Online: 1"),
                hideScoreNumbers,
                Duration.ofSeconds(1L),
                blacklist);
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
}
