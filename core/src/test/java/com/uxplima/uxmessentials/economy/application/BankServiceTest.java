package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

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
        service = new BankService(banks, new NoopTransactionHistory(), events, clock, true);
        leader = new PlayerRef(UUID.randomUUID(), "Leader");
    }

    private SharedBank createBank() {
        return service.createBank("alpha", "Alpha", Currencies.COINS, leader).orElseThrow();
    }

    @Test
    void createBankRejectsADuplicateId() {
        createBank();

        assertThat(service.createBank("alpha", "Other", Currencies.COINS, leader)
                        .errorOrThrow())
                .isEqualTo(BankError.ID_TAKEN);
    }

    @Test
    void depositMovesMoneyAndRaisesOneEvent() {
        createBank();
        wallets.credit(leader, Money.of(Currencies.COINS, 500));

        Result<Unit, BankError> result = service.deposit(leader, "alpha", Money.of(Currencies.COINS, 200));

        assertThat(result.isOk()).isTrue();
        assertThat(banks.findById("alpha").orElseThrow().balance()).isEqualTo(Money.of(Currencies.COINS, 200));
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
        createBank();
        wallets.credit(leader, Money.of(Currencies.COINS, 50));

        Result<Unit, BankError> result = service.deposit(leader, "alpha", Money.of(Currencies.COINS, 200));

        assertThat(result.errorOrThrow()).isEqualTo(BankError.INSUFFICIENT_FUNDS);
        assertThat(banks.findById("alpha").orElseThrow().balance().isZero()).isTrue();
        assertThat(wallets.findByOwner(leader).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 50));
    }

    @Test
    void withdrawMovesMoneyAndRaisesOneEvent() {
        createBank();
        wallets.credit(leader, Money.of(Currencies.COINS, 500));
        service.deposit(leader, "alpha", Money.of(Currencies.COINS, 300));
        events = new CapturingEvents();
        service = new BankService(
                banks, new NoopTransactionHistory(), events, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), true);

        Result<Unit, BankError> result = service.withdraw(leader, "alpha", Money.of(Currencies.COINS, 120));

        assertThat(result.isOk()).isTrue();
        assertThat(banks.findById("alpha").orElseThrow().balance()).isEqualTo(Money.of(Currencies.COINS, 180));
        assertThat(events.published()).hasSize(1).first().isInstanceOf(BankWithdrawn.class);
    }

    @Test
    void withdrawBeyondTheBankBalanceIsInsufficientBankFunds() {
        createBank();
        wallets.credit(leader, Money.of(Currencies.COINS, 500));
        service.deposit(leader, "alpha", Money.of(Currencies.COINS, 100));

        Result<Unit, BankError> result = service.withdraw(leader, "alpha", Money.of(Currencies.COINS, 250));

        assertThat(result.errorOrThrow()).isEqualTo(BankError.INSUFFICIENT_BANK_FUNDS);
        assertThat(banks.findById("alpha").orElseThrow().balance()).isEqualTo(Money.of(Currencies.COINS, 100));
    }

    @Test
    void aNonMemberWithoutPermissionIsDenied() {
        createBank();
        PlayerRef stranger = new PlayerRef(UUID.randomUUID(), "Stranger");
        wallets.credit(stranger, Money.of(Currencies.COINS, 100));

        assertThat(service.deposit(stranger, "alpha", Money.of(Currencies.COINS, 10))
                        .errorOrThrow())
                .isEqualTo(BankError.NO_PERMISSION);
    }

    @Test
    void addingAnExistingMemberIsAlreadyMember() {
        createBank();
        assertThat(service.addMember(leader, "alpha", leader, BankRole.MEMBER).errorOrThrow())
                .isEqualTo(BankError.ALREADY_MEMBER);
    }

    @Test
    void theLeaderCannotBeRemoved() {
        createBank();
        assertThat(service.removeMember(leader, "alpha", leader).errorOrThrow())
                .isEqualTo(BankError.CANNOT_REMOVE_LEADER);
    }

    @Test
    void aForeignProviderRefusesBankMoneyMoves() {
        BankService foreign = new BankService(
                banks, new NoopTransactionHistory(), events, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), false);
        createBank();

        assertThat(foreign.deposit(leader, "alpha", Money.of(Currencies.COINS, 10))
                        .errorOrThrow())
                .isEqualTo(BankError.PROVIDER_UNSUPPORTED);
        assertThat(foreign.withdraw(leader, "alpha", Money.of(Currencies.COINS, 10))
                        .errorOrThrow())
                .isEqualTo(BankError.PROVIDER_UNSUPPORTED);
    }
}
