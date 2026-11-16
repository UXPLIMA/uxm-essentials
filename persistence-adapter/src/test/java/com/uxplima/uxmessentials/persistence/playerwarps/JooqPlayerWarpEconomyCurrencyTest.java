package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.persistence.playerwarps.JooqPlayerWarpEconomy.PayoutConfig;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.ExecuteType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A player warp use charged on one server and settled on another, where the default currency differs: the visitor is
 * refunded, and the owner paid, in the currency the visitor was charged in, and an id the settling server does not
 * know is neither refunded nor paid in another currency.
 */
@EveryBackend
class JooqPlayerWarpEconomyCurrencyTest {

    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems")).build();
    private static final BigDecimal FEE = new BigDecimal("10");
    private static final PayoutConfig NO_CUT = new PayoutConfig(BigDecimal.ZERO, false);

    /** One database of balances and one of warps, shared by both servers as a network shares them. */
    private final Bank bank = new Bank();

    private DSLContext dsl;
    /** The same database, running {@link #afterNextBankRead} after its next read. */
    private DSLContext racing;

    private PlayerWarpId market;
    private boolean offlineWrites = true;
    /** The players on the settling server. */
    private final Set<UUID> here = new HashSet<>();

    private final RecordingLogger log = new RecordingLogger();
    /** Runs once right after the next read of the warp bank, as a concurrent write landing between read and write. */
    private @Nullable Runnable afterNextBankRead;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
        racing = dsl.configuration().deriveAppending(new AfterBankRead()).dsl();
        market = new JooqPlayerWarpRepository(dsl)
                .save(PlayerWarp.create(
                        SAM,
                        "Sam",
                        PlayerWarpName.of("market"),
                        Position.of(WORLD, 0, 64, 0),
                        Instant.parse("2030-01-01T12:00:00Z")));
        bank.credit(ALEX, Money.of(COINS, new BigDecimal("50")));
    }

    /** A server's player warp economy, whose default currency is {@code byDefault}. */
    private JooqPlayerWarpEconomy serverWith(Currency byDefault) {
        return new JooqPlayerWarpEconomy(
                racing,
                bank,
                CurrencyRegistry.of(List.of(COINS, GEMS), byDefault.id()),
                CurrencyBackendRegistry.of(List.of(new Native())),
                NO_CUT,
                here::contains,
                log);
    }

    /** Set the bank as another server's write would, behind the economy under test. */
    private void bankHolds(String amount, Currency currency) {
        dsl.transaction(tx -> tx.dsl()
                .update(PLAYER_WARPS)
                .set(PLAYER_WARPS.EARNED_AMOUNT, new BigDecimal(amount))
                .set(PLAYER_WARPS.EARNED_CURRENCY, currency.id().value())
                .where(PLAYER_WARPS.ID.eq(market.value()))
                .execute());
    }

    @Test
    void aChargeAnswersTheCurrencyItTookAndTheRefundElsewhereGivesThatOneBack() {
        JooqPlayerWarpEconomy lobby = serverWith(COINS);
        JooqPlayerWarpEconomy survival = serverWith(GEMS);

        String takenIn = lobby.charge(ALEX, FEE, "default").orElseThrow();
        Result<Unit, ChargeError> refunded = survival.refund(ALEX, FEE, takenIn);

        assertThat(takenIn).isEqualTo("coins");
        assertThat(refunded.isOk()).isTrue();
        assertThat(bank.balance(ALEX, COINS).amount()).isEqualByComparingTo("50");
        assertThat(bank.balance(ALEX, GEMS).amount()).isEqualByComparingTo("0");
    }

    @Test
    void theOwnerIsPaidInTheCurrencyTheVisitorWasChargedInWhereTheDefaultDiffers() {
        JooqPlayerWarpEconomy lobby = serverWith(COINS);
        JooqPlayerWarpEconomy survival = serverWith(GEMS);

        String takenIn = lobby.charge(ALEX, FEE, "default").orElseThrow();
        assertThat(survival.accrue(market, FEE, takenIn).isOk()).isTrue();
        survival.withdraw(market, SAM);

        assertThat(bank.balance(SAM, COINS).amount()).isEqualByComparingTo("10");
        assertThat(bank.balance(SAM, GEMS).amount()).isEqualByComparingTo("0");
    }

    @Test
    void aShareInAnotherCurrencyThanTheBankHoldsIsPaidStraightToTheOwnerAndTheBankKeepsItsOwn() {
        JooqPlayerWarpEconomy survival = serverWith(GEMS);
        assertThat(survival.accrue(market, new BigDecimal("30"), "gems").isOk()).isTrue();

        assertThat(survival.accrue(market, FEE, "coins").isOk()).isTrue();
        survival.withdraw(market, SAM);

        assertThat(bank.balance(SAM, COINS).amount()).isEqualByComparingTo("10");
        assertThat(bank.balance(SAM, GEMS).amount()).isEqualByComparingTo("30");
    }

    @Test
    void aShareThatCannotBeBankedOrReachAnOfflineOwnerPaysNobodySoTheVisitorIsRefunded() {
        offlineWrites = false;
        JooqPlayerWarpEconomy survival = serverWith(GEMS);
        bankHolds("30", GEMS);

        Result<Unit, ChargeError> accrued = survival.accrue(market, FEE, "coins");

        assertThat(accrued.errorOrThrow()).isEqualTo(ChargeError.ACCRUAL_FAILED);
        assertThat(bank.balance(SAM, COINS).amount()).isEqualByComparingTo("0");
        assertThat(log.warnings()).anyMatch(line -> line.contains("bank_currency_mismatch_not_offline"));
    }

    @Test
    void aShareThatCannotBeBankedIsPaidToAnOwnerWhoIsOnThisServerEvenInAnOnlineOnlyCurrency() {
        offlineWrites = false;
        here.add(SAM.uuid());
        JooqPlayerWarpEconomy survival = serverWith(GEMS);
        bankHolds("30", GEMS);

        assertThat(survival.accrue(market, FEE, "coins").isOk()).isTrue();

        assertThat(bank.balance(SAM, COINS).amount()).isEqualByComparingTo("10");
    }

    @Test
    void aFailedPayoutReturningToABankNowInAnotherCurrencyIsPaidToTheOwnerAndTheBankKeepsItsOwn() {
        JooqPlayerWarpEconomy survival = serverWith(GEMS);
        bankHolds("30", GEMS);
        // The first payout is refused, and meanwhile the emptied bank fills with coins from another landing.
        bank.refuseNextCreditTo(SAM, () -> bankHolds("10", COINS));

        Result<Unit, ChargeError> withdrawn = survival.withdraw(market, SAM);
        survival.withdraw(market, SAM);

        assertThat(withdrawn.isErr()).isTrue();
        assertThat(bank.balance(SAM, GEMS).amount()).isEqualByComparingTo("30");
        assertThat(bank.balance(SAM, COINS).amount()).isEqualByComparingTo("10");
        assertThat(log.errors()).noneMatch(line -> line.contains("playerwarp_reaccrue_lost"));
    }

    @Test
    void aFailedPayoutThatCanGoNowhereIsLoggedWithItsAmountAndCurrency() {
        offlineWrites = false;
        JooqPlayerWarpEconomy survival = serverWith(GEMS);
        bankHolds("30", GEMS);
        bank.refuseNextCreditTo(SAM, () -> bankHolds("10", COINS));

        survival.withdraw(market, SAM);

        assertThat(bank.balance(SAM, GEMS).amount()).isEqualByComparingTo("0");
        assertThat(log.errors())
                .anyMatch(line -> line.contains("playerwarp_reaccrue_lost")
                        && line.contains("currency=gems")
                        && line.contains("amount=30"));
    }

    @Test
    void aWithdrawWhoseBankRefilledToTheSameAmountInAnotherCurrencyPaysNothingInTheOneItRead() {
        JooqPlayerWarpEconomy survival = serverWith(GEMS);
        bankHolds("30", GEMS);
        // Between the read and the zeroing, the gems are paid out elsewhere and 30 coins arrive.
        afterNextBankRead = () -> bankHolds("30", COINS);

        survival.withdraw(market, SAM);
        assertThat(bank.balance(SAM, GEMS).amount()).isEqualByComparingTo("0");

        survival.withdraw(market, SAM);
        assertThat(bank.balance(SAM, COINS).amount()).isEqualByComparingTo("30");
    }

    @Test
    void rentFromABankThatRefilledToTheSameAmountInAnotherCurrencyIsNotTakenFromIt() {
        JooqPlayerWarpEconomy survival = serverWith(GEMS);
        bankHolds("30", GEMS);
        bank.credit(SAM, Money.of(GEMS, new BigDecimal("5")));
        afterNextBankRead = () -> bankHolds("30", COINS);

        Result<Unit, ChargeError> rent = survival.collectRent(market, SAM, FEE, "gems");
        survival.withdraw(market, SAM);

        assertThat(rent.errorOrThrow()).isEqualTo(ChargeError.INSUFFICIENT_FUNDS);
        assertThat(bank.balance(SAM, COINS).amount()).isEqualByComparingTo("30");
        assertThat(bank.balance(SAM, GEMS).amount()).isEqualByComparingTo("5");
    }

    @Test
    void aCurrencyTheSettlingServerDoesNotKnowIsNeitherRefundedNorPaidInItsDefault() {
        JooqPlayerWarpEconomy survival = serverWith(GEMS);

        Result<Unit, ChargeError> refunded = survival.refund(ALEX, FEE, "shells");
        Result<Unit, ChargeError> accrued = survival.accrue(market, FEE, "shells");
        survival.withdraw(market, SAM);

        assertThat(refunded.isErr()).isTrue();
        assertThat(accrued.isErr()).isTrue();
        assertThat(bank.balance(ALEX, GEMS).amount()).isEqualByComparingTo("0");
        assertThat(bank.balance(SAM, GEMS).amount()).isEqualByComparingTo("0");
    }

    @Test
    void aRecordWrittenAsDefaultBeforeChargesResolvedItIsRefundedInThisServersDefault() {
        JooqPlayerWarpEconomy survival = serverWith(GEMS);

        assertThat(survival.refund(ALEX, FEE, "default").isOk()).isTrue();

        assertThat(bank.balance(ALEX, GEMS).amount()).isEqualByComparingTo("10");
    }

    /** Runs {@link #afterNextBankRead} once a read has finished, before the economy's next statement. */
    private final class AfterBankRead implements ExecuteListener {

        @Override
        public void end(ExecuteContext ctx) {
            Runnable next = afterNextBankRead;
            if (ctx.type() == ExecuteType.READ && next != null) {
                afterNextBankRead = null;
                next.run();
            }
        }
    }

    /** The one backend every currency here lives on; whether it can write an offline wallet is the test's choice. */
    private final class Native implements CurrencyBackend {

        @Override
        public String id() {
            return "native";
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public boolean worksOffline() {
            return offlineWrites;
        }

        @Override
        public boolean atomicDebit() {
            return true;
        }

        @Override
        public Precision precision() {
            return Precision.DECIMAL;
        }

        @Override
        public Money balance(PlayerRef owner, Currency currency) {
            return bank.balance(owner, currency);
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            return bank.credit(owner, amount);
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            return bank.debit(owner, amount);
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }
    }

    /** Balances per player and currency; a debit past the balance is refused. */
    private final class Bank implements EconomyProvider {

        private final Map<String, BigDecimal> held = new HashMap<>();
        private @Nullable PlayerRef refused;
        private @Nullable Runnable onRefusal;

        /** Refuse the next credit to {@code who}, running {@code meanwhile} first as a concurrent write would land. */
        void refuseNextCreditTo(PlayerRef who, Runnable meanwhile) {
            this.refused = who;
            this.onRefusal = meanwhile;
        }

        private static String key(PlayerRef owner, Currency currency) {
            return owner.uuid() + "/" + currency.id().value();
        }

        @Override
        public boolean hasAccount(PlayerRef owner, Currency currency) {
            return true;
        }

        @Override
        public void ensureAccount(PlayerRef owner, Currency currency) {}

        @Override
        public Money balance(PlayerRef owner, Currency currency) {
            return Money.of(currency, held.getOrDefault(key(owner, currency), BigDecimal.ZERO));
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            Runnable meanwhile = onRefusal;
            if (owner.equals(refused) && meanwhile != null) {
                refused = null;
                onRefusal = null;
                meanwhile.run();
                return Result.err(TransferError.TARGET_DISABLED);
            }
            if (!offlineWrites && !here.contains(owner.uuid())) {
                return Result.err(TransferError.PLAYER_OFFLINE);
            }
            held.merge(key(owner, amount.currency()), amount.amount(), BigDecimal::add);
            return Result.ok();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            BigDecimal now = held.getOrDefault(key(owner, amount.currency()), BigDecimal.ZERO);
            if (now.compareTo(amount.amount()) < 0) {
                return Result.err(TransferError.INSUFFICIENT_FUNDS);
            }
            held.put(key(owner, amount.currency()), now.subtract(amount.amount()));
            return Result.ok();
        }

        @Override
        public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw new UnsupportedOperationException("a player warp use never transfers");
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }

        @Override
        public Set<Currency> currencies() {
            return Set.of(COINS, GEMS);
        }
    }

    private static final class RecordingLogger implements Logger {

        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        List<String> warnings() {
            return List.copyOf(warnings);
        }

        List<String> errors() {
            return List.copyOf(errors);
        }

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message + " " + java.util.Arrays.toString(args));
        }

        @Override
        public void error(String message, Throwable cause) {
            errors.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}
