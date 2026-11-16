package com.uxplima.uxmessentials.persistence.economy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.economy.domain.Banknote;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Banknotes through the real migrations: a note is cashed once, however many hands reach for it at once. */
@EveryBackend
class JooqBanknoteStoreTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();

    private JooqBanknoteStore notes;

    @BeforeEach
    void open(TestDatabase database) {
        notes = new JooqBanknoteStore(database.dsl());
    }

    @Test
    void aRegisteredNoteIsRedeemedOnce() {
        Banknote note = Banknote.createFresh(Money.of(COINS, new BigDecimal("25.50")));
        notes.register(note);

        assertThat(notes.redeem(note.token())).isTrue();
        assertThat(notes.redeem(note.token())).isFalse();
    }

    @Test
    void aNoteNeverWrittenIsNotRedeemed() {
        assertThat(notes.redeem(UUID.fromString("00000000-0000-0000-0000-00000000beef")))
                .isFalse();
    }

    @Test
    void redeemsRacingForOneNoteCashItOnce() throws Exception {
        Banknote note = Banknote.createFresh(Money.of(COINS, new BigDecimal("10")));
        notes.register(note);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> redeems = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            redeems.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return notes.redeem(note.token());
            }));
        }
        start.countDown();

        int cashed = 0;
        for (CompletableFuture<Boolean> redeem : redeems) {
            if (redeem.get(30, TimeUnit.SECONDS)) {
                cashed++;
            }
        }

        assertThat(cashed).isEqualTo(1);
    }
}
