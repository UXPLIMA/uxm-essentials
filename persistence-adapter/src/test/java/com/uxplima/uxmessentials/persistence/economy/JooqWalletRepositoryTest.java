package com.uxplima.uxmessentials.persistence.economy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Wallet balances through the real migrations: every move is all or nothing, and no debit overdraws. */
@EveryBackend
class JooqWalletRepositoryTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Alex");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a2"), "Sam");
    private static final PlayerRef ROBIN =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a3"), "Robin");
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).max(new BigDecimal("1000")).build();
    private static final Currency GEMS =
            Currency.builder(CurrencyId.of("gems")).max(new BigDecimal("100")).build();

    private JooqWalletRepository wallets;

    @BeforeEach
    void open(TestDatabase database) {
        wallets = new JooqWalletRepository(
                database.dsl(),
                CurrencyRegistry.of(List.of(COINS, GEMS), COINS.id()),
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void aCreditIsReadBackToTheCent() {
        wallets.credit(ALEX, coins("12.34"));
        wallets.credit(ALEX, coins("0.66"));

        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("13.00");
    }

    @Test
    void anOwnerWithNoRowHasNoWalletUntilEnsured() {
        assertThat(wallets.findByOwner(ALEX)).isEmpty();

        wallets.ensureOwner(ALEX);

        assertThat(wallets.findByOwner(ALEX)).isPresent();
        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("0");
    }

    @Test
    void aSetBalanceReplacesTheOldOne() {
        wallets.credit(ALEX, coins("40"));

        wallets.upsertBalance(ALEX, coins("7.5"));

        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("7.5");
    }

    @Test
    void aDebitShortOfFundsTakesNothing() {
        wallets.credit(ALEX, coins("5"));

        assertThat(error(wallets.debit(ALEX, coins("5.01")))).isEqualTo(TransferError.INSUFFICIENT_FUNDS);
        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("5");
    }

    @Test
    void aTransferMovesTheWholeAmountFromOneOwnerToTheOther() {
        wallets.credit(ALEX, coins("30"));

        assertThat(wallets.transfer(ALEX, SAM, coins("12.5")).isOk()).isTrue();

        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("17.5");
        assertThat(balance(SAM, COINS)).isEqualByComparingTo("12.5");
    }

    @Test
    void aTransferThatWouldOverfillTheRecipientLeavesTheSenderPaidNothing() {
        wallets.credit(ALEX, coins("300"));
        wallets.credit(SAM, coins("900"));

        assertThat(error(wallets.transfer(ALEX, SAM, coins("200")))).isEqualTo(TransferError.BALANCE_MAX_EXCEEDED);

        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("300");
        assertThat(balance(SAM, COINS)).isEqualByComparingTo("900");
    }

    @Test
    void anExchangeThatWouldOverfillTheTargetCurrencyLeavesTheSourceUntouched() {
        wallets.credit(ALEX, coins("50"));
        wallets.credit(ALEX, Money.of(GEMS, new BigDecimal("95")));

        assertThat(error(wallets.exchange(ALEX, coins("50"), Money.of(GEMS, new BigDecimal("10")))))
                .isEqualTo(TransferError.BALANCE_MAX_EXCEEDED);
        assertThat(wallets.exchange(ALEX, coins("20"), Money.of(GEMS, new BigDecimal("5")))
                        .isOk())
                .isTrue();

        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("30");
        assertThat(balance(ALEX, GEMS)).isEqualByComparingTo("100");
    }

    @Test
    void debitsRacingForOneBalanceNeverOverdrawIt() throws Exception {
        wallets.credit(ALEX, coins("10"));
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> debits = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            debits.add(CompletableFuture.supplyAsync(() -> {
                await(start);
                return wallets.debit(ALEX, coins("1")).isOk();
            }));
        }
        start.countDown();

        long taken = 0;
        for (CompletableFuture<Boolean> debit : debits) {
            if (debit.get(30, TimeUnit.SECONDS)) {
                taken++;
            }
        }

        assertThat(taken).isEqualTo(10);
        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("0");
    }

    @Test
    void theLeaderboardIsRichestFirstWithEachOwnersName() {
        wallets.ensureOwner(ALEX);
        wallets.ensureOwner(SAM);
        wallets.ensureOwner(ROBIN);
        wallets.credit(ALEX, coins("5"));
        wallets.credit(SAM, coins("50"));
        wallets.credit(ROBIN, coins("20"));
        wallets.credit(ROBIN, Money.of(GEMS, new BigDecimal("99")));

        List<BaltopRow> top = wallets.top(COINS, 2);

        assertThat(top).extracting(row -> row.owner().name()).containsExactly("Sam", "Robin");
        assertThat(top.getFirst().balance().amount()).isEqualByComparingTo("50");
    }

    private BigDecimal balance(PlayerRef owner, Currency currency) {
        return wallets.findByOwner(owner).orElseThrow().balanceOf(currency).amount();
    }

    private static Money coins(String amount) {
        return Money.of(COINS, new BigDecimal(amount));
    }

    private static TransferError error(Result<Unit, TransferError> result) {
        return result.errorOrThrow();
    }

    private static void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
