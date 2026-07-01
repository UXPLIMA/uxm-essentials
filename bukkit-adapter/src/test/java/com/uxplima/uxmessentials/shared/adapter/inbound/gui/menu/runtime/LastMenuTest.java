package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Pure coverage of the {@link LastMenu} tracker: record/get, per-player overwrite, argument copy, and clear. */
class LastMenuTest {

    @Test
    void getReturnsEmptyForAnUnknownPlayer() {
        LastMenu tracker = new LastMenu();

        assertThat(tracker.get(UUID.randomUUID())).isEmpty();
    }

    @Test
    void recordThenGetReturnsTheSameOpen() {
        LastMenu tracker = new LastMenu();
        UUID player = UUID.randomUUID();

        tracker.record(player, new LastMenu.LastOpen("shop", 2, Map.of("who", "Steve")));

        LastMenu.LastOpen open = tracker.get(player).orElseThrow();
        assertThat(open.menuId()).isEqualTo("shop");
        assertThat(open.page()).isEqualTo(2);
        assertThat(open.arguments()).containsEntry("who", "Steve");
    }

    @Test
    void recordingASecondOpenOverwritesTheFirst() {
        LastMenu tracker = new LastMenu();
        UUID player = UUID.randomUUID();

        tracker.record(player, new LastMenu.LastOpen("shop", 0, Map.of()));
        tracker.record(player, new LastMenu.LastOpen("bank", 1, Map.of()));

        assertThat(tracker.get(player).orElseThrow().menuId()).isEqualTo("bank");
    }

    @Test
    void argumentsAreCopiedSoLaterMutationDoesNotLeakIn() {
        LastMenu tracker = new LastMenu();
        UUID player = UUID.randomUUID();
        Map<String, String> args = new HashMap<>();
        args.put("who", "Steve");

        tracker.record(player, new LastMenu.LastOpen("shop", 0, args));
        args.put("who", "Alex");

        assertThat(tracker.get(player).orElseThrow().arguments()).containsEntry("who", "Steve");
    }

    @Test
    void clearForgetsThePlayersOpen() {
        LastMenu tracker = new LastMenu();
        UUID player = UUID.randomUUID();
        tracker.record(player, new LastMenu.LastOpen("shop", 0, Map.of()));

        tracker.clear(player);

        assertThat(tracker.get(player)).isEmpty();
    }
}
