package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankRole;
import com.uxplima.uxmessentials.economy.domain.event.BankDeposited;
import com.uxplima.uxmessentials.economy.domain.event.BankWithdrawn;
import com.uxplima.uxmessentials.economy.fakes.CapturingEvents;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.economy.fakes.InMemoryBankRepository;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWalletRepository;
import com.uxplima.uxmessentials.economy.fakes.NoopTransactionHistory;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The bank service orchestration: deposit and withdraw route through the atomic repository, each applied move
 * raises exactly one event, every distinct failure maps to its own {@link BankError}, and a foreign provider
 * refuses the money moves rather than risking a non-atomic transfer.
 */
class BankServiceTest {

    private InMemoryWalletRepository wallets;
    private InMemoryBankRepository banks;
    private CapturingEvents events;
    private BankService service;
    private PlayerRef leader;

    @BeforeEach
    void setUp() {
        wallets = new InMemoryWalletRepository();
        banks = new InMemoryBankRepository(wallets);
        events = new CapturingEvents();
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        service = new BankService(banks, new NoopTransactionHistory(), events, clock, new java.util.Random(1), true);
        leader = new PlayerRef(UUID.randomUUID(), "Leader");
    }

    private SharedBank createBank() {
        return service.createBank("Alpha", Currencies.COINS, leader).orElseThrow();
    }

    @Test
    void createBankAssignsAnEightCharAlphanumericId() {
        SharedBank bank = createBank();

        assertThat(bank.id()).hasSize(8).matches("[a-zA-Z0-9]{8}");
        assertThat(banks.findById(bank.id())).isPresent();
    }

    @Test
    void everyCreatedBankGetsADistinctId() {
        SharedBank first = createBank();
        SharedBank second = service.createBank("Beta", Currencies.COINS, leader).orElseThrow();

        assertThat(second.id()).isNotEqualTo(first.id());
    }

    @Test
    void aCollidingDrawRetriesAgainstTheRepository() {
        // The first whole id draws all-zeros ("aaaaaaaa"); the second create draws that same id once (a
        // collision) and then a distinct id, so the bank that lands is the retried, fresh draw.
        RandomGenerator colliding = new IdSequenceGenerator(0, 0, 1);
        BankService retrying = new BankService(
                banks,
                new NoopTransactionHistory(),
                events,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                colliding,
                true);
        String taken = retrying.createBank("First", Currencies.COINS, leader)
                .orElseThrow()
                .id();

        String next = retrying.createBank("Second", Currencies.COINS, leader)
                .orElseThrow()
                .id();

        assertThat(next).isNotEqualTo(taken);
        assertThat(banks.findById(taken)).isPresent();
        assertThat(banks.findById(next)).isPresent();
    }

    @Test
    void exhaustingTheBoundedRetriesFailsWithIdTaken() {
        // A generator pinned so every id draws all-zeros can only ever produce one id, so the second create
        // exhausts every bounded attempt against the now-taken id and fails rather than looping forever.
        RandomGenerator pinned = new IdSequenceGenerator(0);
        BankService stuck = new BankService(
                banks, new NoopTransactionHistory(), events, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), pinned, true);
        stuck.createBank("First", Currencies.COINS, leader).orElseThrow();

        assertThat(stuck.createBank("Second", Currencies.COINS, leader).errorOrThrow())
                .isEqualTo(BankError.ID_TAKEN);
    }

    @Test
    void depositMovesMoneyAndRaisesOneEvent() {
        String id = createBank().id();
        wallets.credit(leader, Money.of(Currencies.COINS, 500));

        Result<Unit, BankError> result = service.deposit(leader, id, Money.of(Currencies.COINS, 200));

        assertThat(result.isOk()).isTrue();
        assertThat(banks.findById(id).orElseThrow().balance()).isEqualTo(Money.of(Currencies.COINS, 200));
        assertThat(wallets.findByOwner(leader).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 300));
        assertThat(events.published()).hasSize(1).first().isInstanceOf(BankDeposited.class);
    }

    @Test
    void depositToAMissingBankIsNotFound() {
        assertThat(service.deposit(leader, "ghost", Money.of(Currencies.COINS, 10))
                        .errorOrThrow())
                .isEqualTo(BankError.NOT_FOUND);
    }

    @Test
    void depositShortOfFundsIsInsufficientAndMovesNothing() {
        String id = createBank().id();
        wallets.credit(leader, Money.of(Currencies.COINS, 50));

        Result<Unit, BankError> result = service.deposit(leader, id, Money.of(Currencies.COINS, 200));

        assertThat(result.errorOrThrow()).isEqualTo(BankError.INSUFFICIENT_FUNDS);
        assertThat(banks.findById(id).orElseThrow().balance().isZero()).isTrue();
        assertThat(wallets.findByOwner(leader).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 50));
    }

    @Test
    void withdrawMovesMoneyAndRaisesOneEvent() {
        String id = createBank().id();
        wallets.credit(leader, Money.of(Currencies.COINS, 500));
        service.deposit(leader, id, Money.of(Currencies.COINS, 300));
        events = new CapturingEvents();
        service = new BankService(
                banks,
                new NoopTransactionHistory(),
                events,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                new java.util.Random(1),
                true);

        Result<Unit, BankError> result = service.withdraw(leader, id, Money.of(Currencies.COINS, 120));

        assertThat(result.isOk()).isTrue();
        assertThat(banks.findById(id).orElseThrow().balance()).isEqualTo(Money.of(Currencies.COINS, 180));
        assertThat(events.published()).hasSize(1).first().isInstanceOf(BankWithdrawn.class);
    }

    @Test
    void withdrawBeyondTheBankBalanceIsInsufficientBankFunds() {
        String id = createBank().id();
        wallets.credit(leader, Money.of(Currencies.COINS, 500));
        service.deposit(leader, id, Money.of(Currencies.COINS, 100));

        Result<Unit, BankError> result = service.withdraw(leader, id, Money.of(Currencies.COINS, 250));

        assertThat(result.errorOrThrow()).isEqualTo(BankError.INSUFFICIENT_BANK_FUNDS);
        assertThat(banks.findById(id).orElseThrow().balance()).isEqualTo(Money.of(Currencies.COINS, 100));
    }

    @Test
    void aNonMemberWithoutPermissionIsDenied() {
        String id = createBank().id();
        PlayerRef stranger = new PlayerRef(UUID.randomUUID(), "Stranger");
        wallets.credit(stranger, Money.of(Currencies.COINS, 100));

        assertThat(service.deposit(stranger, id, Money.of(Currencies.COINS, 10)).errorOrThrow())
                .isEqualTo(BankError.NO_PERMISSION);
    }

    @Test
    void addingAnExistingMemberIsAlreadyMember() {
        String id = createBank().id();
        assertThat(service.addMember(leader, id, leader, BankRole.MEMBER).errorOrThrow())
                .isEqualTo(BankError.ALREADY_MEMBER);
    }

    @Test
    void theLeaderCannotBeRemoved() {
        String id = createBank().id();
        assertThat(service.removeMember(leader, id, leader).errorOrThrow()).isEqualTo(BankError.CANNOT_REMOVE_LEADER);
    }

    @Test
    void aForeignProviderRefusesBankMoneyMoves() {
        BankService foreign = new BankService(
                banks,
                new NoopTransactionHistory(),
                events,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                new java.util.Random(1),
                false);
        String id = createBank().id();

        assertThat(foreign.deposit(leader, id, Money.of(Currencies.COINS, 10)).errorOrThrow())
                .isEqualTo(BankError.PROVIDER_UNSUPPORTED);
        assertThat(foreign.withdraw(leader, id, Money.of(Currencies.COINS, 10)).errorOrThrow())
                .isEqualTo(BankError.PROVIDER_UNSUPPORTED);
    }

    /**
     * A {@link RandomGenerator} that emits one constant digit per whole generated id: every {@code nextInt} call
     * for the eight characters of one id returns the same value, then the next id advances to the next entry in
     * the sequence (repeating the last entry once exhausted). With two distinct entries this draws two distinct
     * ids; pinned to one entry it can only ever draw one. This makes the bounded collision retry deterministic.
     */
    private static final class IdSequenceGenerator implements RandomGenerator {

        private static final int ID_LENGTH = 8;

        private final int[] values;
        private int draws;

        IdSequenceGenerator(int... values) {
            this.values = values.clone();
        }

        @Override
        public long nextLong() {
            return nextInt();
        }

        @Override
        public int nextInt() {
            int idIndex = draws / ID_LENGTH;
            draws++;
            return values[Math.min(idIndex, values.length - 1)];
        }

        @Override
        public int nextInt(int bound) {
            return Math.floorMod(nextInt(), bound);
        }
    }
}
