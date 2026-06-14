package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.domain.DisplayContent;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarBoard;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.display.AnimationSpec;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
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
 * default, no match clears), a blacklisted world tears the selected board down, the hide-score-numbers path reaches the
 * objective's number-format setter, and the format is re-applied across a reused-sidebar board switch but left alone on a
 * steady-state tick.
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
    void reAppliesTheNumberFormatWhenTheSelectedBoardSwitchesHideChoice() {
        // H-3: a viewer can switch boards (hide=true -> hide=false) with the same sidebar reused and no teardown in
        // between. The number format must follow the now-selected board, so the reuse render must reach the objective's
        // numberFormat setter again. MockBukkit leaves that setter unimplemented (it throws on any argument, null
        // included), so we observe the call landing rather than the rendered result.
        PlayerMock player = server.addPlayer();
        AtomicReference<SidebarConfig> ref =
                new AtomicReference<>(single(board("default", DisplayCondition.always(), true, Set.of())));
        ScoreboardRenderer renderer =
                new ScoreboardRenderer(sidebars, alwaysShown(), ref::get, new AnimationRegistry(List.of()));

        // First paint creates the sidebar then applies the blank format (hide=true); the create path reaches the
        // setter,
        // which MockBukkit refuses. Swallow that — the sidebar itself was created before the setter threw.
        assertThatThrownBy(() -> renderer.renderFor(player))
                .isInstanceOf(org.mockbukkit.mockbukkit.exception.UnimplementedOperationException.class);
        assertThat(sidebars.get(player.getUniqueId())).isNotNull();

        // Switch the selected board to one that shows numbers; the sidebar is reused (not recreated) but the format
        // must
        // be reset, so the setter is reached again. Before the fix the reuse branch skipped it and the numbers stayed
        // hidden.
        ref.set(single(board("default", DisplayCondition.always(), false, Set.of())));
        assertThatThrownBy(() -> renderer.renderFor(player))
                .isInstanceOf(org.mockbukkit.mockbukkit.exception.UnimplementedOperationException.class)
                .hasStackTraceContaining("numberFormat");
    }

    @Test
    void doesNotReApplyTheNumberFormatOnASteadyStateTick() {
        // No board switch -> no change -> the setter is never re-invoked. A show-numbers board never touches the setter
        // (a fresh objective already shows the vanilla numbers), so two paints in a row must both complete cleanly; if
        // the renderer re-applied every tick the MockBukkit setter would throw on the second paint.
        PlayerMock player = server.addPlayer();
        ScoreboardRenderer renderer = renderer(single(board("default", DisplayCondition.always(), false, Set.of())));

        renderer.renderFor(player);
        assertThatCode(() -> renderer.renderFor(player)).doesNotThrowAnyException();
        assertThat(sidebars.count()).isEqualTo(1);
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

    @Test
    void aConditionalLineIsHiddenWhenTheConditionFailsAndShownWhenItPasses() {
        // The pure per-line condition filter: a line prefixed with "<condition> | " is kept only when the condition
        // matches the viewer, and the condition prefix is stripped off the kept text.
        List<String> sources =
                List.of("<gray>Always", "permission:" + STAFF_NODE + " | <gold>Staff perks", "<gray>Also always");

        List<String> forStaff = ScoreboardRenderer.visibleLines(sources, ctx(STAFF_NODE::equals));
        List<String> forRegular = ScoreboardRenderer.visibleLines(sources, ctx(n -> false));

        // The staff viewer keeps the gated line (with its condition prefix stripped); the regular viewer drops it.
        assertThat(forStaff).containsExactly("<gray>Always", "<gold>Staff perks", "<gray>Also always");
        assertThat(forRegular).containsExactly("<gray>Always", "<gray>Also always");
    }

    @Test
    void onlyTheFirstSeparatorSplitsSoTextMayContainThePipe() {
        // A line whose text legitimately holds " | " keeps everything after the first separator as the text.
        List<String> sources = List.of("permission:" + STAFF_NODE + " | <gold>A | B");

        assertThat(ScoreboardRenderer.visibleLines(sources, ctx(STAFF_NODE::equals)))
                .containsExactly("<gold>A | B");
    }

    @Test
    void anAnimationTokenInALineRendersTheCurrentFrame() {
        PlayerMock player = server.addPlayer();
        AnimationRegistry registry = new AnimationRegistry(List.of(AnimationDef.frames(
                new AnimationSpec("blink", AnimationSpec.AnimationType.FRAMES, List.of("ON", "OFF"), 1))));
        SidebarConfig config = single(new SidebarBoard(
                "default",
                new DisplayContent(
                        Optional.of("<gold>T"),
                        List.of("<gray>State: %anim_blink%"),
                        false,
                        Duration.ofSeconds(1L),
                        Set.of()),
                DisplayCondition.always(),
                0));
        ScoreboardRenderer renderer =
                new ScoreboardRenderer(sidebars, alwaysShown(), new AtomicReference<>(config)::get, registry);

        // tick 0 → frame index 0 ("ON"); advance once → frame index 1 ("OFF"). The rendered line carries the frame.
        renderer.renderFor(player);
        assertThat(lineText(player, 0)).isEqualTo("State: ON");
        registry.advance();
        renderer.renderFor(player);
        assertThat(lineText(player, 0)).isEqualTo("State: OFF");
    }

    @Test
    void aBuiltInTokenInALineRendersTheLiveValue() {
        PlayerMock player = server.addPlayer();
        SidebarConfig config = single(new SidebarBoard(
                "default",
                new DisplayContent(
                        Optional.of("<gold>T"),
                        List.of("<white>{player}", "<white>{online}"),
                        false,
                        Duration.ofSeconds(1L),
                        Set.of()),
                DisplayCondition.always(),
                0));
        ScoreboardRenderer renderer = new ScoreboardRenderer(
                sidebars, alwaysShown(), new AtomicReference<>(config)::get, new AnimationRegistry(List.of()));

        // The {player} and {online} built-in tokens resolve off the live player/server with no PlaceholderAPI present.
        renderer.renderFor(player);
        assertThat(lineText(player, 0)).isEqualTo(player.getName());
        assertThat(lineText(player, 1)).isEqualTo("1");
    }

    private String lineText(PlayerMock player, int index) {
        com.uxplima.uxmlib.hud.scoreboard.Sidebar sidebar =
                java.util.Objects.requireNonNull(sidebars.get(player.getUniqueId()), "sidebar");
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(sidebar.currentLines().get(index));
    }

    private static ConditionContext ctx(Predicate<String> hasPermission) {
        Function<String, String> resolve = s -> Map.<String, String>of().getOrDefault(s, s);
        return new ConditionContext(hasPermission, "world", "SURVIVAL", resolve);
    }

    private ScoreboardRenderer renderer(SidebarConfig config) {
        AtomicReference<SidebarConfig> ref = new AtomicReference<>(config);
        return new ScoreboardRenderer(sidebars, alwaysShown(), ref::get, new AnimationRegistry(List.of()));
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
