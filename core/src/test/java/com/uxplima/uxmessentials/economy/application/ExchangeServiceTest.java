package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.ExchangeRate;
import com.uxplima.uxmessentials.economy.domain.ExchangeRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.Wallet;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.Test;

/** An exchange debits one currency and credits another together, never pays out more than was taken in. */
class ExchangeServiceTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final Currency GEMS =
            Currency.builder(CurrencyId.of("gems")).max(new BigDecimal("100")).build();
    private static final Currency TICKETS =
            Currency.builder(CurrencyId.of("tickets")).exchangeAllowed(false).build();

    private final InMemoryBank bank = new InMemoryBank(COINS, GEMS, TICKETS);
    private final Ledger ledger = new Ledger();

    private ExchangeService service(ExchangeRate... rates) {
        return new ExchangeService(ledger, new ExchangeRegistry(List.of(rates)), true);
    }

    private static ExchangeRate rate(Currency from, Currency to, String rate, String fee) {
        return new ExchangeRate(from.id(), to.id(), new BigDecimal(rate), new BigDecimal(fee));
    }

    private BigDecimal balance(Currency currency) {
        return bank.balance(ALEX, currency).amount();
    }

    @Test
    void anExchangeTakesTheSourceAndPaysTheTargetLessTheFee() {
        bank.set(ALEX, Money.of(COINS, 100));

        ExchangeOutcome outcome =
                service(rate(COINS, GEMS, "0.5", "0.1")).exchange(ALEX, new BigDecimal("40"), COINS, GEMS);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.SUCCESS);
        assertThat(outcome.targetAmount()).isEqualByComparingTo("18");
        assertThat(balance(COINS)).isEqualByComparingTo("60");
        assertThat(balance(GEMS)).isEqualByComparingTo("18");
    }

    @Test
    void aShortSourceMovesNothing() {
        bank.set(ALEX, Money.of(COINS, 10));

        ExchangeOutcome outcome =
                service(rate(COINS, GEMS, "1", "0")).exchange(ALEX, new BigDecimal("11"), COINS, GEMS);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.INSUFFICIENT_FUNDS);
        assertThat(balance(COINS)).isEqualByComparingTo("10");
        assertThat(balance(GEMS)).isZero();
    }

    @Test
    void aTargetThatWouldPassItsMaximumKeepsTheSource() {
        bank.set(ALEX, Money.of(COINS, 100));
        bank.set(ALEX, Money.of(GEMS, 95));

        ExchangeOutcome outcome =
                service(rate(COINS, GEMS, "1", "0")).exchange(ALEX, new BigDecimal("10"), COINS, GEMS);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.LIMIT_EXCEEDED);
        assertThat(balance(COINS)).isEqualByComparingTo("100");
        assertThat(balance(GEMS)).isEqualByComparingTo("95");
    }

    @Test
    void anAmountFinerThanTheSourcePrecisionIsPaidOnlyForWhatIsDebited() {
        bank.set(ALEX, Money.of(COINS, 100));

        ExchangeOutcome outcome =
                service(rate(COINS, GEMS, "10", "0")).exchange(ALEX, new BigDecimal("2.004"), COINS, GEMS);

        assertThat(outcome.status()).isEqualTo(ExchangeOutcome.Status.SUCCESS);
        assertThat(balance(COINS)).isEqualByComparingTo("98");
        assertThat(balance(GEMS)).isEqualByComparingTo("20");
    }

    @Test
    void anAmountThatRoundsToNothingAtTheSourcePrecisionPaysNothing() {
        bank.set(ALEX, Money.of(COINS, 100));

        ExchangeOutcome outcome =
                service(rate(COINS, GEMS, "100", "0")).exchange(ALEX, new BigDecimal("0.004"), COINS, GEMS);

        assertThat(outcome.error()).isEqualTo(TransferError.BELOW_MINIMUM);
        assertThat(balance(COINS)).isEqualByComparingTo("100");
        assertThat(balance(GEMS)).isZero();
    }

    @Test
    void aRoundTripBetweenTwoCurrenciesNeverGrowsTheBalance() {
        bank.set(ALEX, Money.of(COINS, new BigDecimal("0.01")));
        ExchangeService service = service(rate(COINS, GEMS, "0.5", "0"), rate(GEMS, COINS, "2", "0"));

        service.exchange(ALEX, new BigDecimal("0.01"), COINS, GEMS);
        service.exchange(ALEX, balance(GEMS), GEMS, COINS);

        assertThat(balance(COINS).add(balance(GEMS).multiply(new BigDecimal("2"))))
                .isLessThanOrEqualTo(new BigDecimal("0.01"));
    }

    @Test
    void refusalsMoveNothing() {
        bank.set(ALEX, Money.of(COINS, 100));
        ExchangeService service = service(rate(COINS, GEMS, "1", "0"), rate(COINS, TICKETS, "1", "0"));

        assertThat(service.exchange(ALEX, BigDecimal.TEN, GEMS, COINS).status())
                .isEqualTo(ExchangeOutcome.Status.RATE_NOT_FOUND);
        assertThat(service.exchange(ALEX, BigDecimal.TEN, COINS, TICKETS).status())
                .isEqualTo(ExchangeOutcome.Status.CURRENCY_DISABLED);
        assertThat(service.exchange(ALEX, new BigDecimal("-5"), COINS, GEMS).error())
                .isEqualTo(TransferError.BELOW_MINIMUM);
        assertThat(new ExchangeService(ledger, new ExchangeRegistry(List.of(rate(COINS, GEMS, "1", "0"))), false)
                        .exchange(ALEX, BigDecimal.TEN, COINS, GEMS)
                        .status())
                .isEqualTo(ExchangeOutcome.Status.PROVIDER_UNSUPPORTED);
        assertThat(balance(COINS)).isEqualByComparingTo("100");
        assertThat(balance(GEMS)).isZero();
    }

    /**
     * The native wallet table as the exchange sees it. Like the guarded {@code UPDATE}, a debit of nothing changes a
     * row and succeeds, and the credit is checked against the target's maximum before anything is kept.
     */
    private final class Ledger implements WalletRepository {

        @Override
        public Result<Unit, TransferError> exchange(PlayerRef owner, Money debit, Money credit) {
            if (bank.balance(owner, debit.currency()).isLessThan(debit)) {
                return Result.err(TransferError.INSUFFICIENT_FUNDS);
            }
            if (bank.balance(owner, credit.currency())
                            .amount()
                            .add(credit.amount())
                            .compareTo(credit.currency().max())
                    > 0) {
                return Result.err(TransferError.BALANCE_MAX_EXCEEDED);
            }
            bank.debit(owner, debit);
            bank.credit(owner, credit);
            return Result.ok();
        }

        @Override
        public Optional<Wallet> findByOwner(PlayerRef owner) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Wallet ensureOwner(PlayerRef owner) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void upsertBalance(PlayerRef owner, Money balance) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, TransferError> transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            throw new UnsupportedOperationException();
        }
    }
}
