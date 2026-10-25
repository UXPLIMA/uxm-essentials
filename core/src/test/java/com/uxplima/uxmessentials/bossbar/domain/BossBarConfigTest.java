package com.uxplima.uxmessentials.bossbar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.display.ConditionParser;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.junit.jupiter.api.Test;

/** Which bars a player sees at a moment, and the clock each one counts. */
class BossBarConfigTest {

    private static final Instant NOW = Instant.parse("2026-10-25T20:00:00Z");

    @Test
    void anAlwaysOnBarIsShownWithNoClock() {
        BossBarConfig config = new BossBarConfig(List.of(bar("website", DisplayCondition.always(), null, false)));

        assertThat(config.shownTo(player("lobby"), NOW, id -> Optional.empty()))
                .containsExactly(new BossBarConfig.Shown(config.bars().get(0), Optional.empty()));
    }

    @Test
    void barsKeepTheOrderTheyAreWrittenIn() {
        BossBarConfig config = new BossBarConfig(List.of(
                bar("website", DisplayCondition.always(), null, false),
                bar("store", DisplayCondition.always(), null, false)));

        assertThat(config.shownTo(player("lobby"), NOW, id -> Optional.empty()))
                .extracting(shown -> shown.bar().id())
                .containsExactly("website", "store");
    }

    @Test
    void aBarWhoseConditionFailsIsNotShown() {
        BossBarConfig config =
                new BossBarConfig(List.of(bar("staff", ConditionParser.parse("world:arena"), null, false)));

        assertThat(config.shownTo(player("lobby"), NOW, id -> Optional.empty())).isEmpty();
    }

    @Test
    void aScheduledBarShowsOnlyInsideItsWindowAndCountsItsEnd() {
        BossBarWindow event =
                new BossBarWindow(Optional.of(NOW.minus(Duration.ofDays(1))), NOW.plus(Duration.ofDays(2)));
        BossBarConfig config = new BossBarConfig(List.of(bar("event", DisplayCondition.always(), event, false)));

        assertThat(config.shownTo(player("lobby"), NOW, id -> Optional.empty()))
                .containsExactly(new BossBarConfig.Shown(config.bars().get(0), Optional.of(event)));
        assertThat(config.shownTo(player("lobby"), NOW.plus(Duration.ofDays(3)), id -> Optional.empty()))
                .isEmpty();
        assertThat(config.shownTo(player("lobby"), NOW.minus(Duration.ofDays(2)), id -> Optional.empty()))
                .isEmpty();
    }

    @Test
    void anAnnouncementBarShowsOnlyWhileAnnounced() {
        BossBarWindow announced = new BossBarWindow(Optional.of(NOW), NOW.plus(Duration.ofSeconds(30)));
        BossBarConfig config = new BossBarConfig(List.of(bar("drop", DisplayCondition.always(), null, true)));

        assertThat(config.shownTo(player("lobby"), NOW, id -> Optional.empty())).isEmpty();
        assertThat(config.shownTo(player("lobby"), NOW, id -> Optional.of(announced)))
                .containsExactly(new BossBarConfig.Shown(config.bars().get(0), Optional.of(announced)));
    }

    @Test
    void anAnnouncementThatEndedShowsNothing() {
        BossBarWindow over = new BossBarWindow(Optional.of(NOW.minusSeconds(60)), NOW.minusSeconds(30));
        BossBarConfig config = new BossBarConfig(List.of(bar("drop", DisplayCondition.always(), null, true)));

        assertThat(config.shownTo(player("lobby"), NOW, id -> Optional.of(over)))
                .isEmpty();
    }

    @Test
    void anAnnouncedBarStillKeepsItsCondition() {
        BossBarWindow announced = new BossBarWindow(Optional.of(NOW), NOW.plus(Duration.ofSeconds(30)));
        BossBarConfig config =
                new BossBarConfig(List.of(bar("drop", ConditionParser.parse("world:arena"), null, true)));

        assertThat(config.shownTo(player("lobby"), NOW, id -> Optional.of(announced)))
                .isEmpty();
    }

    @Test
    void aWindowKnowsWhatIsLeftAndHowMuchOfItRemains() {
        BossBarWindow window = new BossBarWindow(Optional.of(NOW), NOW.plus(Duration.ofHours(4)));

        assertThat(window.remaining(NOW.plus(Duration.ofHours(1)))).isEqualTo(Duration.ofHours(3));
        assertThat(window.fractionLeft(NOW.plus(Duration.ofHours(1)))).isEqualTo(0.75);
        assertThat(window.remaining(NOW.plus(Duration.ofHours(5)))).isEqualTo(Duration.ZERO);
        assertThat(window.fractionLeft(NOW.plus(Duration.ofHours(5)))).isZero();
    }

    @Test
    void aWindowWithNoStartIsFullUntilItEnds() {
        BossBarWindow window = new BossBarWindow(Optional.empty(), NOW.plus(Duration.ofHours(4)));

        assertThat(window.activeAt(NOW.minus(Duration.ofDays(400)))).isTrue();
        assertThat(window.fractionLeft(NOW)).isEqualTo(1.0);
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BossBarWindow(Optional.of(NOW), NOW.minusSeconds(1)));
    }

    @Test
    void anIdWithASpaceIsRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> bar("two words", DisplayCondition.always(), null, false));
    }

    private static BossBarDefinition bar(
            String id,
            DisplayCondition condition,
            @org.jspecify.annotations.Nullable BossBarWindow window,
            boolean announcementOnly) {
        return new BossBarDefinition(
                id,
                "<green>" + id,
                "GREEN",
                "NOTCHED_20",
                "100",
                condition,
                Optional.ofNullable(window),
                announcementOnly);
    }

    private static ConditionContext player(String world) {
        return new ConditionContext(
                node -> false,
                world,
                "SURVIVAL",
                text -> Map.<String, String>of().getOrDefault(text, text));
    }
}
