package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyItemDeliveries.ECONOMY_ITEM_DELIVERIES;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.ItemDeliveryQueue.ItemDelivery;
import com.uxplima.uxmessentials.economy.application.port.ItemDeliveryQueue.Kind;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A delivery is claimed by one server at a time, handed over once, and what did not fit stays for the player's next
 * join; a claim whose server stopped is taken over once it is stale, and one its server still holds is kept fresh.
 * Gives queued while a player is away add up in one row instead of one row per sweep.
 */
@EveryBackend
class JooqItemDeliveriesTest {

    private static final long AT = Instant.parse("2030-01-01T00:00:00Z").toEpochMilli();
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(AT), ZoneOffset.UTC);
    private static final UUID ALEX = new UUID(0L, 1L);
    private static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");

    private DSLContext dsl;
    private JooqItemDeliveries deliveries;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
        deliveries = new JooqItemDeliveries(dsl, CLOCK);
    }

    @Test
    void aDeliveryIsClaimedByOneServerAndHandedOverOnce() {
        queue("d1", ALEX, Kind.GIVE, "25");

        assertThat(deliveries.claim("d1", "survival")).isPresent();
        assertThat(deliveries.claim("d1", "lobby")).isEmpty();
        assertThat(deliveries.claimable(ALEX, true)).isEmpty();
        assertThat(deliveries.finish("d1", "lobby", BigDecimal.ZERO)).isFalse();
        assertThat(deliveries.finish("d1", "survival", BigDecimal.ZERO)).isTrue();

        assertThat(rows()).isZero();
    }

    @Test
    void whatDidNotFitWaitsForTheNextJoinUnderTheReasonOfWhatQueuedIt() {
        queue("d1", BEDROCK, Kind.RESET, "10");
        assertThat(deliveries.waiting(List.of(ALEX, BEDROCK))).containsExactly(BEDROCK);
        assertThat(deliveries.resetsWaiting(BEDROCK)).containsExactly("gold");
        deliveries.claim("d1", "survival");

        deliveries.finish("d1", "survival", new BigDecimal("4"));

        assertThat(deliveries.resetsWaiting(BEDROCK)).isEmpty();
        assertThat(deliveries.waiting(List.of(BEDROCK))).isEmpty();
        assertThat(deliveries.claimable(BEDROCK, false)).isEmpty();
        assertThat(deliveries.claimable(BEDROCK, true)).singleElement().satisfies(rest -> {
            assertThat(rest.kind()).isEqualTo(Kind.GIVE);
            assertThat(rest.amount()).isEqualByComparingTo("4");
            assertThat(rest.reason()).isEqualTo(EconomyReason.ADMIN_RESETALL);
        });
    }

    @Test
    void aClaimGivenBackLeavesTheDeliveryAsItWas() {
        queue("d1", ALEX, Kind.GIVE, "25");
        deliveries.claim("d1", "survival");

        deliveries.release("d1", "survival");

        assertThat(deliveries.claimable(ALEX, false))
                .extracting(ItemDelivery::amount)
                .singleElement()
                .satisfies(amount -> assertThat(amount).isEqualByComparingTo("25"));
    }

    @Test
    void aClaimWhoseServerStoppedIsTakenOverOnceStale() {
        queue("d1", ALEX, Kind.GIVE, "25");
        deliveries.claim("d1", "stopped");
        JooqItemDeliveries later = new JooqItemDeliveries(dsl, CLOCK, Duration.ZERO.minusMillis(1));

        assertThat(later.claimable(ALEX, true)).hasSize(1);
        assertThat(later.claim("d1", "survival")).isPresent();
        assertThat(deliveries.finish("d1", "stopped", BigDecimal.ZERO)).isFalse();
        assertThat(later.finish("d1", "survival", BigDecimal.ZERO)).isTrue();
    }

    @Test
    void aClaimItsServerKeepsRefreshingDoesNotGoStale() {
        queue("d1", ALEX, Kind.GIVE, "25");
        deliveries.claim("d1", "survival");
        Clock tenMinutesOn = Clock.offset(CLOCK, JooqItemDeliveries.STALE_AFTER.plusMinutes(1));
        JooqItemDeliveries refreshing = new JooqItemDeliveries(dsl, tenMinutesOn);

        assertThat(refreshing.touch("d1", "survival")).isTrue();
        assertThat(refreshing.touch("d1", "lobby")).isFalse();

        assertThat(refreshing.claimable(ALEX, true)).isEmpty();
        assertThat(refreshing.claim("d1", "lobby")).isEmpty();
    }

    @Test
    void givesQueuedWhileAPlayerIsAwayAddUpInOneRow() {
        for (int sweep = 0; sweep < 5; sweep++) {
            deliveries.queueGive(
                    UUID.randomUUID().toString(),
                    List.of(ALEX, BEDROCK),
                    "gold",
                    new BigDecimal("10"),
                    EconomyReason.ADMIN_GIVEALL);
        }

        assertThat(rows()).isEqualTo(2);
        assertThat(deliveries.claimable(ALEX, true)).singleElement().satisfies(give -> {
            assertThat(give.amount()).isEqualByComparingTo("50");
            assertThat(give.reason()).isEqualTo(EconomyReason.ADMIN_GIVEALL);
        });
    }

    @Test
    void aGiveQueuedAfterAClaimIsKeptApartAndOneQueuedBeforeIsHandedOverWithIt() {
        deliveries.queueGive("s1", List.of(ALEX), "gold", new BigDecimal("10"), EconomyReason.ADMIN_GIVEALL);
        String first = deliveries.claimable(ALEX, true).getFirst().id();
        deliveries.queueGive("s2", List.of(ALEX), "gold", new BigDecimal("5"), EconomyReason.ADMIN_GIVEALL);

        ItemDelivery claimed = deliveries.claim(first, "survival").orElseThrow();
        assertThat(claimed.amount()).isEqualByComparingTo("15");

        deliveries.queueGive("s3", List.of(ALEX), "gold", new BigDecimal("7"), EconomyReason.ADMIN_GIVEALL);
        assertThat(deliveries.claimable(ALEX, true))
                .singleElement()
                .satisfies(give -> assertThat(give.amount()).isEqualByComparingTo("7"));
    }

    @Test
    void aHeldBackRestJoinsTheGiveAlreadyWaiting() {
        deliveries.queueGive("s1", List.of(ALEX), "gold", new BigDecimal("10"), EconomyReason.ADMIN_GIVEALL);
        String first = deliveries.claimable(ALEX, true).getFirst().id();
        deliveries.claim(first, "survival");
        deliveries.queueGive("s2", List.of(ALEX), "gold", new BigDecimal("5"), EconomyReason.ADMIN_GIVEALL);

        assertThat(deliveries.finish(first, "survival", new BigDecimal("3"))).isTrue();

        assertThat(rows()).isEqualTo(1);
        assertThat(deliveries.claimable(ALEX, true))
                .singleElement()
                .satisfies(give -> assertThat(give.amount()).isEqualByComparingTo("8"));
    }

    private void queue(String id, UUID owner, Kind kind, String amount) {
        EconomyReason reason = kind == Kind.RESET ? EconomyReason.ADMIN_RESETALL : EconomyReason.ADMIN_GIVEALL;
        dsl.transaction(configuration -> configuration
                .dsl()
                .insertInto(ECONOMY_ITEM_DELIVERIES)
                .set(ECONOMY_ITEM_DELIVERIES.ID, id)
                .set(ECONOMY_ITEM_DELIVERIES.SWEEP_ID, "sweep-1")
                .set(ECONOMY_ITEM_DELIVERIES.OWNER, owner.toString())
                .set(ECONOMY_ITEM_DELIVERIES.CURRENCY, "gold")
                .set(ECONOMY_ITEM_DELIVERIES.KIND, kind.name())
                .set(ECONOMY_ITEM_DELIVERIES.AMOUNT, new BigDecimal(amount))
                .set(ECONOMY_ITEM_DELIVERIES.REASON, reason.name())
                .set(ECONOMY_ITEM_DELIVERIES.CREATED_AT, AT)
                .set(ECONOMY_ITEM_DELIVERIES.HELD_BACK, 0)
                .execute());
    }

    private int rows() {
        return dsl.fetchCount(ECONOMY_ITEM_DELIVERIES);
    }
}
