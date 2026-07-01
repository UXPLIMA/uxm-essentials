package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Pure coverage of the {@link LastMenu} history stack: record/get, push/back, dedupe, bound, argument copy, clear. */
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
    void getPeeksTheMostRecentlyRecordedOpen() {
        LastMenu tracker = new LastMenu();
        UUID player = UUID.randomUUID();

        tracker.record(player, new LastMenu.LastOpen("shop", 0, Map.of()));
        tracker.record(player, new LastMenu.LastOpen("bank", 1, Map.of()));

        assertThat(tracker.get(player).orElseThrow().menuId()).isEqualTo("bank");
    }

    @Test
    void backStepsToThePreviousOpenAndLeavesItOnTop() {
        LastMenu tracker = new LastMenu();
        UUID player = UUID.randomUUID();
        tracker.record(player, new LastMenu.LastOpen("a", 0, Map.of()));
        tracker.record(player, new LastMenu.LastOpen("b", 0, Map.of()));

        assertThat(tracker.back(player).orElseThrow().menuId()).isEqualTo("a");
        // The previous is now the current, so /menu last (get) sees it too.
        assertThat(tracker.get(player).orElseThrow().menuId()).isEqualTo("a");
    }

    @Test
    void backFromTheOnlyOpenReturnsEmptyAndEmptiesTheHistory() {
        LastMenu tracker = new LastMenu();
        UUID player = UUID.randomUUID();
        tracker.record(player, new LastMenu.LastOpen("a", 0, Map.of()));

        assertThat(tracker.back(player)).isEmpty();
        assertThat(tracker.get(player)).isEmpty();
    }

    @Test
    void backOnAnEmptyHistoryReturnsEmpty() {
        LastMenu tracker = new LastMenu();

        assertThat(tracker.back(UUID.randomUUID())).isEmpty();
    }

    @Test
    void aConsecutiveIdenticalOpenIsNotStackedAsADuplicate() {
        LastMenu tracker = new LastMenu();
        UUID player = UUID.randomUUID();
        tracker.record(player, new LastMenu.LastOpen("a", 0, Map.of()));
        tracker.record(player, new LastMenu.LastOpen("a", 0, Map.of())); // a refresh: same menu/page/args
        tracker.record(player, new LastMenu.LastOpen("b", 0, Map.of()));

        // Only one "a" beneath "b": back reaches it once, then the history is exhausted.
        assertThat(tracker.back(player).orElseThrow().menuId()).isEqualTo("a");
        assertThat(tracker.back(player)).isEmpty();
    }

    @Test
    void aDifferentPageOfTheSameMenuIsStacked() {
        LastMenu tracker = new LastMenu();
        UUID player = UUID.randomUUID();
        tracker.record(player, new LastMenu.LastOpen("a", 0, Map.of()));
        tracker.record(player, new LastMenu.LastOpen("a", 1, Map.of())); // a page flip is a distinct open

        assertThat(tracker.back(player).orElseThrow().page()).isZero();
    }

    @Test
    void theHistoryIsBoundedAndEvictsTheOldest() {
        LastMenu tracker = new LastMenu();
        UUID player = UUID.randomUUID();
        // Push well past the cap; each open is distinct so none is de-duplicated.
        for (int i = 0; i < 100; i++) {
            tracker.record(player, new LastMenu.LastOpen("menu-" + i, 0, Map.of()));
        }

        // The cap is 32, so 32 opens remain: the top plus 31 below it, then the history exhausts.
        int steps = 0;
        while (tracker.back(player).isPresent()) {
            steps++;
        }
        assertThat(steps).isEqualTo(31);
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
    void clearForgetsThePlayersHistory() {
        LastMenu tracker = new LastMenu();
        UUID player = UUID.randomUUID();
        tracker.record(player, new LastMenu.LastOpen("shop", 0, Map.of()));
        tracker.record(player, new LastMenu.LastOpen("bank", 0, Map.of()));

        tracker.clear(player);

        assertThat(tracker.get(player)).isEmpty();
        assertThat(tracker.back(player)).isEmpty();
    }
}
