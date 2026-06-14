package com.uxplima.uxmessentials.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.junit.jupiter.api.Test;

/**
 * The {@link Announcement} value object's invariants: a blank id and an empty line set are rejected, an empty
 * channel set defaults to chat, and the mutable inputs (lines, channels) are defensively copied so a later edit to
 * the caller's collection cannot mutate a constructed announcement.
 */
class AnnouncementTest {

    @Test
    void aBlankIdIsRejected() {
        assertThatThrownBy(() -> announcement(" ", List.of("hi"), Set.of(BroadcastChannel.CHAT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void anEmptyLineSetIsRejected() {
        assertThatThrownBy(() -> announcement("tip", List.of(), Set.of(BroadcastChannel.CHAT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("least one line");
    }

    @Test
    void anEmptyChannelSetDefaultsToChat() {
        Announcement result = announcement("tip", List.of("hi"), Set.of());

        assertThat(result.channels()).containsExactly(BroadcastChannel.CHAT);
    }

    @Test
    void theRequestedChannelsAreRetainedWhenPresent() {
        Announcement result =
                announcement("tip", List.of("hi"), Set.of(BroadcastChannel.TITLE, BroadcastChannel.ACTION_BAR));

        assertThat(result.channels()).containsExactlyInAnyOrder(BroadcastChannel.TITLE, BroadcastChannel.ACTION_BAR);
    }

    @Test
    void linesAreDefensivelyCopied() {
        List<String> source = new ArrayList<>(List.of("first", "second"));
        Announcement result = announcement("tip", source, Set.of(BroadcastChannel.CHAT));

        source.add("smuggled");

        assertThat(result.lines()).containsExactly("first", "second");
    }

    @Test
    void channelsAreDefensivelyCopied() {
        Set<BroadcastChannel> source = new HashSet<>(Set.of(BroadcastChannel.CHAT));
        Announcement result = announcement("tip", List.of("hi"), source);

        source.add(BroadcastChannel.BOSS_BAR);

        assertThat(result.channels()).containsExactly(BroadcastChannel.CHAT);
    }

    @Test
    void aFullyPopulatedAnnouncementRetainsEveryField() {
        Announcement result = new Announcement(
                "welcome",
                List.of("line one", "line two"),
                new DisplayCondition.Permission("uxmessentials.vip"),
                Optional.of(Duration.ofSeconds(90)),
                Set.of(BroadcastChannel.TITLE),
                Optional.of("minecraft:entity.player.levelup"),
                true);

        assertThat(result.id()).isEqualTo("welcome");
        assertThat(result.lines()).containsExactly("line one", "line two");
        assertThat(result.condition()).isInstanceOf(DisplayCondition.Permission.class);
        assertThat(result.intervalOverride()).contains(Duration.ofSeconds(90));
        assertThat(result.channels()).containsExactly(BroadcastChannel.TITLE);
        assertThat(result.sound()).contains("minecraft:entity.player.levelup");
        assertThat(result.centered()).isTrue();
    }

    private static Announcement announcement(String id, List<String> lines, Set<BroadcastChannel> channels) {
        return new Announcement(
                id, lines, DisplayCondition.always(), Optional.empty(), channels, Optional.empty(), false);
    }
}
