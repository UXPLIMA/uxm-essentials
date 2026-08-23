package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.domain.DisplayContent;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarBoard;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarConfig;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarLine;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarNumberFormat;
import com.uxplima.uxmessentials.scoreboard.support.RecordingScoreboardPackets;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardDisplaySlot;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardNumberFormat;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardObjectiveAction;
import com.uxplima.uxmlib.packet.scoreboard.ScoreboardPacketEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class ScoreboardRendererTest {

    private ServerMock server;
    private RecordingScoreboardPackets packets;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        packets = new RecordingScoreboardPackets();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void firstFrameIsBundledAndUsesCompactVisibleScores() {
        PlayerMock player = server.addPlayer();
        ScoreboardRenderer renderer = renderer(content(List.of(legacy("one", "First"), legacy("two", "Second"))));

        renderer.renderFor(player);

        assertThat(packets.sends()).hasSize(1);
        assertThat(packets.operations())
                .hasExactlyElementsOfTypes(
                        RecordingScoreboardPackets.Create.class,
                        RecordingScoreboardPackets.SetScore.class,
                        RecordingScoreboardPackets.SetScore.class,
                        RecordingScoreboardPackets.Display.class);
        assertThat(scores()).extracting(s -> s.score().score()).containsExactly(2, 1);
    }

    @Test
    void unchangedFrameEmitsNoPackets() {
        PlayerMock player = server.addPlayer();
        ScoreboardRenderer renderer = renderer(content(List.of(legacy("one", "First"))));

        renderer.renderFor(player);
        packets.reset();
        renderer.renderFor(player);

        assertThat(packets.operations()).isEmpty();
    }

    @Test
    void literalEmptySpacersKeepSeparateStableHolders() {
        PlayerMock player = server.addPlayer();
        ScoreboardRenderer renderer =
                renderer(content(List.of(legacy("spacer-a", ""), legacy("spacer-b", ""), legacy("text", "Visible"))));

        renderer.renderFor(player);

        assertThat(scores())
                .extracting(s -> s.score().holder())
                .containsExactly("uxm:spacer-a", "uxm:spacer-b", "uxm:text");
        assertThat(scores().get(0).score().displayName()).isEqualTo(net.kyori.adventure.text.Component.empty());
    }

    @Test
    void conditionsAndEmptyFilteringRunBeforeTheFifteenLineLimit() {
        PlayerMock player = server.addPlayer();
        List<SidebarLine> candidates = new ArrayList<>();
        candidates.add(new SidebarLine(
                "hidden", "Hidden", new DisplayCondition.Permission("never"), SidebarNumberFormat.blank(), false));
        candidates.add(new SidebarLine("empty", "", DisplayCondition.always(), SidebarNumberFormat.blank(), true));
        for (int i = 0; i < 16; i++) {
            candidates.add(legacy("visible-" + i, "Line " + i));
        }
        ScoreboardRenderer renderer = renderer(content(candidates));

        renderer.renderFor(player);

        assertThat(scores()).hasSize(15);
        assertThat(scores())
                .extracting(s -> s.score().holder())
                .startsWith("uxm:visible-0")
                .doesNotContain("uxm:hidden", "uxm:empty", "uxm:visible-15");
    }

    @Test
    void fixedRightTextIsRenderedIndependentlyFromTheLineText() {
        PlayerMock player = server.addPlayer();
        SidebarLine balance = new SidebarLine(
                "balance", "<gray>Balance", DisplayCondition.always(), SidebarNumberFormat.fixed("<gold>12"), false);
        ScoreboardRenderer renderer = renderer(content(List.of(balance)));

        renderer.renderFor(player);

        assertThat(scores().getFirst().score().numberFormat())
                .isEqualTo(ScoreboardNumberFormat.fixed(net.kyori.adventure.text.Component.text("12")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)));
    }

    @Test
    void aForeignSidebarYieldsThenRedisplaysAfterRemoval() {
        PlayerMock player = server.addPlayer();
        ScoreboardRenderer renderer = renderer(content(List.of(legacy("one", "First"))));
        renderer.renderFor(player);
        packets.reset();

        assertThat(renderer.observe(
                        player.getUniqueId(),
                        new ScoreboardPacketEvent.Display(ScoreboardDisplaySlot.SIDEBAR, "other")))
                .isFalse();
        assertThat(renderer.yielded(player.getUniqueId())).isTrue();
        renderer.renderFor(player);
        assertThat(packets.operations()).isEmpty();

        assertThat(renderer.observe(
                        player.getUniqueId(),
                        new ScoreboardPacketEvent.Objective("other", ScoreboardObjectiveAction.REMOVE)))
                .isTrue();
        renderer.renderFor(player);
        assertThat(packets.operations())
                .containsExactly(new RecordingScoreboardPackets.Display(
                        ScoreboardDisplaySlot.SIDEBAR, ScoreboardRenderer.OBJECTIVE_NAME));
    }

    @Test
    void clearWhileYieldedDoesNotClearTheForeignDisplaySlot() {
        PlayerMock player = server.addPlayer();
        ScoreboardRenderer renderer = renderer(content(List.of(legacy("one", "First"))));
        renderer.renderFor(player);
        renderer.observe(
                player.getUniqueId(), new ScoreboardPacketEvent.Display(ScoreboardDisplaySlot.SIDEBAR, "other"));
        packets.reset();

        renderer.clear(player);

        assertThat(packets.operations())
                .containsExactly(new RecordingScoreboardPackets.RemoveObjective(ScoreboardRenderer.OBJECTIVE_NAME));
    }

    private List<RecordingScoreboardPackets.SetScore> scores() {
        return packets.operations().stream()
                .filter(RecordingScoreboardPackets.SetScore.class::isInstance)
                .map(RecordingScoreboardPackets.SetScore.class::cast)
                .toList();
    }

    private ScoreboardRenderer renderer(DisplayContent content) {
        AtomicReference<SidebarConfig> config = new AtomicReference<>(
                new SidebarConfig(List.of(new SidebarBoard("default", content, DisplayCondition.always(), 0))));
        return new ScoreboardRenderer(packets, alwaysShown(), config::get, new AnimationRegistry(List.of()));
    }

    private static DisplayContent content(List<SidebarLine> lines) {
        return DisplayContent.typed(Optional.of("<gold>Server"), lines, true, Duration.ofSeconds(1), Set.of());
    }

    private static SidebarLine legacy(String id, String text) {
        return new SidebarLine(id, text, DisplayCondition.always(), SidebarNumberFormat.blank(), false);
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
