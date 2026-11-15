package com.uxplima.uxmessentials.shared.application.teleport;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.SteppedScheduler;
import org.junit.jupiter.api.Test;

/** The money of one paid teleport: kept on a landing, given back once otherwise, and never lost without a line. */
class PaidTeleportsTest {

    private static final String SUBJECT = PaidTeleports.subject("warp", "shop", ALEX, new BigDecimal("10"), "gold");

    private final SteppedScheduler scheduler = new SteppedScheduler(new MutableClock(NOW));
    private final RecordingLogger log = new RecordingLogger();
    private final PaidTeleports paid = new PaidTeleports(scheduler, log);
    private final List<String> moved = new ArrayList<>();
    private boolean keepThrows;

    private PaidTeleports.Settlement open() {
        return paid.open(
                SUBJECT,
                () -> {
                    if (keepThrows) {
                        throw new IllegalStateException("the bank write failed");
                    }
                    moved.add("owner paid");
                    return true;
                },
                () -> moved.add("refunded"),
                () -> moved.add("told"));
    }

    @Test
    void aLandingKeepsTheChargeAndPaysItOn() {
        CompletableFuture<TeleportOutcome> teleport = new CompletableFuture<>();
        open().follow(teleport);

        teleport.complete(TeleportOutcome.LANDED);

        assertThat(moved).containsExactly("owner paid");
    }

    @Test
    void aRefusalBeforeTheLaunchAndALateOutcomeSettleOnce() {
        PaidTeleports.Settlement settlement = open();
        CompletableFuture<TeleportOutcome> teleport = new CompletableFuture<>();

        settlement.refuse();
        settlement.follow(teleport);
        teleport.complete(TeleportOutcome.LANDED);

        assertThat(moved).containsExactly("refunded", "told");
    }

    @Test
    void aPayoutThatThrowsGivesTheChargeBackAsTheCrossServerSettlementDoes() {
        keepThrows = true;
        CompletableFuture<TeleportOutcome> teleport = new CompletableFuture<>();
        open().follow(teleport);

        teleport.complete(TeleportOutcome.LANDED);

        assertThat(moved).containsExactly("refunded", "told");
        assertThat(log.errors())
                .singleElement()
                .satisfies(line -> assertThat(line)
                        .contains("player_refunded")
                        .contains(ALEX.uuid().toString()));
    }

    @Test
    void aSettlementStillOpenAtStopIsLoggedAndALaterOutcomeMovesNoMoney() {
        CompletableFuture<TeleportOutcome> teleport = new CompletableFuture<>();
        open().follow(teleport);
        PaidTeleports.Settlement settled = open();
        settled.refuse();
        moved.clear();

        paid.close();
        teleport.complete(TeleportOutcome.CANCELLED);

        assertThat(moved).isEmpty();
        assertThat(log.errors())
                .singleElement()
                .satisfies(line -> assertThat(line)
                        .contains("paid_teleport_unsettled")
                        .contains(ALEX.uuid().toString())
                        .contains("amount=10"));
    }
}
