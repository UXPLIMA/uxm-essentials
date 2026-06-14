package com.uxplima.uxmessentials.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.junit.jupiter.api.Test;

/**
 * The {@link AnnouncerConfig} aggregate's invariants: {@link AnnouncerConfig#empty()} carries no announcements and
 * never fires; the announcement list is defensively copied; a non-positive interval and a negative min-players are
 * rejected; and the {@code shouldFire} gate combines the has-announcements check with the min-players threshold.
 */
class AnnouncerConfigTest {

    @Test
    void emptyHasNoAnnouncementsAndNeverFires() {
        AnnouncerConfig config = AnnouncerConfig.empty();

        assertThat(config.hasAnnouncements()).isFalse();
        assertThat(config.announcementCount()).isZero();
        assertThat(config.shouldFire(100)).isFalse();
    }

    @Test
    void theAnnouncementListIsDefensivelyCopied() {
        List<Announcement> source = new ArrayList<>(List.of(announcement("a")));
        AnnouncerConfig config = new AnnouncerConfig(Duration.ofMinutes(5), 0, Ordering.SEQUENTIAL, source);

        source.add(announcement("smuggled"));

        assertThat(config.announcementCount()).isEqualTo(1);
        assertThat(config.announcementAt(0).id()).isEqualTo("a");
    }

    @Test
    void aNonPositiveIntervalIsRejected() {
        assertThatThrownBy(() -> new AnnouncerConfig(Duration.ZERO, 0, Ordering.SEQUENTIAL, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("interval");
    }

    @Test
    void aNegativeMinPlayersIsRejected() {
        assertThatThrownBy(() -> new AnnouncerConfig(Duration.ofMinutes(5), -1, Ordering.SEQUENTIAL, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minOnlinePlayers");
    }

    @Test
    void shouldFireHonoursTheMinPlayersGate() {
        AnnouncerConfig config =
                new AnnouncerConfig(Duration.ofMinutes(5), 3, Ordering.SEQUENTIAL, List.of(announcement("a")));

        assertThat(config.shouldFire(2)).isFalse();
        assertThat(config.shouldFire(3)).isTrue();
    }

    private static Announcement announcement(String id) {
        return new Announcement(
                id,
                List.of("line"),
                DisplayCondition.always(),
                Optional.empty(),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty(),
                false);
    }
}
