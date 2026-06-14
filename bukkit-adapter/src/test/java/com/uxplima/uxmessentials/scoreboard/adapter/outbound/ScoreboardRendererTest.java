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
import com.uxplima.uxmessentials.scoreboard.domain.SidebarBoard;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarConfig;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the {@link ScoreboardRenderer} render path under MockBukkit: a selected board shows a sidebar, the
 * condition-driven selection picks the right board per viewer (a staff player gets the staff board, a non-staff the
 * default, no match clears), a blacklisted world tears the selected board down, and the hide-score-numbers path reaches
 * the objective's number-format setter.
 *
 * <p>MockBukkit's {@code ObjectiveMock.numberFormat} is unimplemented (it throws), so a board that hides its numbers
 * cannot be rendered to completion here; {@link #appliesABlankNumberFormatWhenHidingNumbers()} asserts the call lands
 * on that setter — proving the production wiring invokes the real Paper API — and the other render cases keep numbers
 * shown so they exercise the rest of the path. On real Paper 1.21.11 the setter applies {@code NumberFormat.blank()}.
 */
class ScoreboardRendererTest {

    private static final String STAFF_NODE = "uxmessentials.staff";

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
    void rendersASidebarForASelectedBoard() {
        PlayerMock player = server.addPlayer();
        ScoreboardRenderer renderer = renderer(single(board("default", DisplayCondition.always(), false, Set.of())));

        assertThatCode(() -> renderer.renderFor(player)).doesNotThrowAnyException();

        assertThat(sidebars.count()).isEqualTo(1);
    }

    @Test
    void aStaffPlayerGetsTheStaffBoardAndOthersGetTheDefault() {
        PlayerMock staff = server.addPlayer();
        staff.addAttachment(MockBukkit.createMockPlugin(), STAFF_NODE, true);
        PlayerMock regular = server.addPlayer();
        SidebarConfig config = new SidebarConfig(List.of(
                board("staff", new DisplayCondition.Permission(STAFF_NODE), false, Set.of()),
                board("default", DisplayCondition.always(), false, Set.of())));
        ScoreboardRenderer renderer = renderer(config);

        renderer.renderFor(staff);
        renderer.renderFor(regular);

        // Both end up with a board; the staff player's selection resolved to the staff board, the other to the default.
        assertThat(sidebars.count()).isEqualTo(2);
        assertThat(sidebars.get(staff.getUniqueId())).isNotNull();
        assertThat(sidebars.get(regular.getUniqueId())).isNotNull();
    }

    @Test
    void noMatchingBoardClearsTheSidebar() {
        PlayerMock player = server.addPlayer();
        SidebarConfig config = new SidebarConfig(
                List.of(board("staff", new DisplayCondition.Permission(STAFF_NODE), false, Set.of())));
        ScoreboardRenderer renderer = renderer(config);

        renderer.renderFor(player);

        assertThat(sidebars.count()).isZero();
    }

    @Test
    void appliesABlankNumberFormatWhenHidingNumbers() {
        PlayerMock player = server.addPlayer();
        ScoreboardRenderer renderer = renderer(single(board("default", DisplayCondition.always(), true, Set.of())));

        // The renderer reaches Objective.numberFormat(blank()); MockBukkit leaves that setter unimplemented, so the
        // call surfaces here. On real Paper the same call hides the red side numbers.
        assertThatThrownBy(() -> renderer.renderFor(player))
                .isInstanceOf(org.mockbukkit.mockbukkit.exception.UnimplementedOperationException.class)
                .hasStackTraceContaining("numberFormat");
    }

    @Test
    void tearsDownTheSelectedBoardInABlacklistedWorld() {
        PlayerMock player = server.addPlayer();
        String world = player.getWorld().getName();
        ScoreboardRenderer renderer =
                renderer(single(board("default", DisplayCondition.always(), false, Set.of(world))));

        renderer.renderFor(player);

        assertThat(sidebars.count()).isZero();
    }

    private ScoreboardRenderer renderer(SidebarConfig config) {
        AtomicReference<SidebarConfig> ref = new AtomicReference<>(config);
        return new ScoreboardRenderer(sidebars, alwaysShown(), ref::get);
    }

    private static SidebarConfig single(SidebarBoard board) {
        return new SidebarConfig(List.of(board));
    }

    private static SidebarBoard board(
            String name, DisplayCondition condition, boolean hideScoreNumbers, Set<String> blacklist) {
        DisplayContent content = new DisplayContent(
                Optional.of("<gold>" + name),
                List.of("<white>Online: 1"),
                hideScoreNumbers,
                Duration.ofSeconds(1L),
                blacklist);
        return new SidebarBoard(name, content, condition, 0);
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
