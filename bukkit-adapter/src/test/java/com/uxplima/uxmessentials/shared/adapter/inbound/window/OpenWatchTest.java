package com.uxplima.uxmessentials.shared.adapter.inbound.window;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * A window asked for is confirmed from its open event, only when nothing cancelled it, and at most once. One that
 * does not come in time confirms nothing when the same window opens much later.
 */
class OpenWatchTest {

    private static final UUID ANN = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private long now;
    private final OpenWatch<String> watch = new OpenWatch<>(() -> now);
    private final List<String> told = new ArrayList<>();

    @Test
    void anOpenThatWentThroughIsConfirmedOnce() {
        watch.expect(ANN, "shop"::equals, () -> told.add("shop"));

        watch.decided(ANN, "shop", false);
        watch.decided(ANN, "shop", false);

        assertThat(told).containsExactly("shop");
    }

    @Test
    void aCancelledOpenIsNotConfirmedAndUsesTheEntryUp() {
        watch.expect(ANN, "shop"::equals, () -> told.add("shop"));

        watch.decided(ANN, "shop", true);
        watch.decided(ANN, "shop", false);

        assertThat(told).isEmpty();
    }

    @Test
    void anotherWindowOrAnotherPlayerConfirmsNothing() {
        watch.expect(ANN, "shop"::equals, () -> told.add("shop"));

        watch.decided(ANN, "warps", false);
        watch.decided(BEN, "shop", false);
        assertThat(told).isEmpty();

        watch.decided(ANN, "shop", false);
        assertThat(told).containsExactly("shop");
    }

    @Test
    void aWindowThatComesTooLateConfirmsNothing() {
        watch.expect(ANN, "shop"::equals, () -> told.add("shop"));

        now += OpenWatch.WAIT.toNanos();
        watch.decided(ANN, "shop", false);

        assertThat(told).isEmpty();
    }

    @Test
    void aWatchGivenItsOwnWaitConfirmsAWindowThatComesWithinIt() {
        OpenWatch<String> patient = new OpenWatch<>(() -> now, OpenWatch.WAIT.multipliedBy(30));
        patient.expect(ANN, "shop"::equals, () -> told.add("shop"));

        now += OpenWatch.WAIT.multipliedBy(29).toNanos();
        patient.decided(ANN, "shop", false);

        assertThat(told).containsExactly("shop");
    }

    @Test
    void aPlayerWhoLeftIsForgotten() {
        watch.expect(ANN, "shop"::equals, () -> told.add("shop"));

        watch.forget(ANN);
        watch.decided(ANN, "shop", false);

        assertThat(told).isEmpty();
    }
}
