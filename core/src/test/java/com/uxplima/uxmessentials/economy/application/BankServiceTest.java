package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BankRepository;
import com.uxplima.uxmessentials.economy.application.port.HistoryRecord;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankRole;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import org.junit.jupiter.api.Test;

/** Money moves between a wallet and a shared bank only for a member whose role allows it, and only when covered. */
class BankServiceTest {

    private static final Currency DOLLARS =
            Currency.builder(CurrencyId.of("dollars")).build();
    private static final PlayerRef KAI = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000000c"), "Kai");

    private final InMemoryBank wallets = new InMemoryBank(DOLLARS);
    private final Banks banks = new Banks();
    private final History history = new History();
    private final RecordingEvents events = new RecordingEvents();
    private final BankService service = service(true);

    private BankService service(boolean nativeLedger) {
        return new BankService(banks, history, events, CLOCK, new SplittableRandom(1), nativeLedger);
    }

    private static Money dollars(String amount) {
        return Money.of(DOLLARS, new BigDecimal(amount));
    }

    private BigDecimal wallet(PlayerRef who) {
        return wallets.balance(who, DOLLARS).amount();
    }

    private BigDecimal vault(String id) {
        return banks.findById(id).orElseThrow().balance().amount();
    }

    /** A bank led by Alex, holding 100, with Sam as a plain member and Kai as co-leader. */
    private String fundedBank() {
        String id = service.createBank("guild", DOLLARS, ALEX).orElseThrow().id();
        service.addMember(ALEX, id, SAM, BankRole.MEMBER).orElseThrow();
        service.addMember(ALEX, id, KAI, BankRole.CO_LEADER).orElseThrow();
        wallets.set(ALEX, dollars("100"));
        service.deposit(ALEX, id, dollars("100")).orElseThrow();
        history.lines.clear();
        return id;
    }

    @Test
    void aNewBankIsEmptyAndLedByItsCreator() {
        SharedBank bank = service.createBank("guild", DOLLARS, ALEX).orElseThrow();

        assertThat(bank.id()).hasSize(8).matches("[A-Za-z0-9]+");
        assertThat(bank.balance().isZero()).isTrue();
        assertThat(bank.hasPermission(ALEX, SharedBank.BankAction.DELETE)).isTrue();
        assertThat(service.getBankIdsForPlayer(ALEX)).containsExactly(bank.id());
    }

    @Test
    void anIdThatKeepsCollidingGivesUpWithoutOverwritingTheExistingBank() {
        BankService stuck = new BankService(banks, history, events, CLOCK, () -> 0L, true);
        String first = stuck.createBank("first", DOLLARS, ALEX).orElseThrow().id();

        Result<SharedBank, BankError> second = stuck.createBank("second", DOLLARS, SAM);

        assertThat(second.errorOrThrow()).isEqualTo(BankError.ID_TAKEN);
        assertThat(banks.findById(first).orElseThrow().name()).isEqualTo("first");
    }

    @Test
    void aDepositMovesTheMoneyFromTheWalletIntoTheBankAndRecordsItOnce() {
        String id = fundedBank();
        wallets.set(SAM, dollars("30"));

        assertThat(service.deposit(SAM, id, dollars("30")).isOk()).isTrue();

        assertThat(wallet(SAM)).isZero();
        assertThat(vault(id)).isEqualByComparingTo("130");
        assertThat(history.lines).containsExactly(SAM.uuid() + ">" + id + ":30.00:BANK_DEPOSIT");
    }

    @Test
    void aPlainMemberMayDepositButNotWithdraw() {
        String id = fundedBank();

        assertThat(service.withdraw(SAM, id, dollars("10")).errorOrThrow()).isEqualTo(BankError.NO_PERMISSION);

        assertThat(wallet(SAM)).isZero();
        assertThat(vault(id)).isEqualByComparingTo("100");
        assertThat(history.lines).isEmpty();
    }

    @Test
    void aCoLeaderWithdrawsWhatTheBankHoldsAndNoMore() {
        String id = fundedBank();

        assertThat(service.withdraw(KAI, id, dollars("100.01")).errorOrThrow())
                .isEqualTo(BankError.INSUFFICIENT_BANK_FUNDS);
        assertThat(service.withdraw(KAI, id, dollars("100")).isOk()).isTrue();
        assertThat(service.withdraw(KAI, id, dollars("0.01")).errorOrThrow())
                .isEqualTo(BankError.INSUFFICIENT_BANK_FUNDS);

        assertThat(wallet(KAI)).isEqualByComparingTo("100");
        assertThat(vault(id)).isZero();
        assertThat(history.lines).containsExactly(id + ">" + KAI.uuid() + ":100.00:BANK_WITHDRAW");
    }

    @Test
    void aDepositTheWalletCannotCoverMovesNothing() {
        String id = fundedBank();
        wallets.set(SAM, dollars("5"));

        assertThat(service.deposit(SAM, id, dollars("6")).errorOrThrow()).isEqualTo(BankError.INSUFFICIENT_FUNDS);

        assertThat(wallet(SAM)).isEqualByComparingTo("5");
        assertThat(vault(id)).isEqualByComparingTo("100");
        assertThat(history.lines).isEmpty();
    }

    @Test
    void anOutsiderAnUnknownBankAndAForeignEconomyMoveNothing() {
        String id = fundedBank();
        PlayerRef outsider = new PlayerRef(UUID.randomUUID(), "Outsider");
        wallets.set(outsider, dollars("50"));

        assertThat(service.deposit(outsider, id, dollars("10")).errorOrThrow()).isEqualTo(BankError.NO_PERMISSION);
        assertThat(service.withdraw(outsider, id, dollars("10")).errorOrThrow()).isEqualTo(BankError.NO_PERMISSION);
        assertThat(service.deposit(ALEX, "missing", dollars("10")).errorOrThrow())
                .isEqualTo(BankError.NOT_FOUND);
        assertThat(service(false).withdraw(ALEX, id, dollars("10")).errorOrThrow())
                .isEqualTo(BankError.PROVIDER_UNSUPPORTED);

        assertThat(wallet(outsider)).isEqualByComparingTo("50");
        assertThat(wallet(ALEX)).isZero();
        assertThat(vault(id)).isEqualByComparingTo("100");
    }

    @Test
    void onlyARoleThatManagesMembersChangesTheRosterAndTheLeaderStays() {
        String id = fundedBank();
        PlayerRef newcomer = new PlayerRef(UUID.randomUUID(), "Newcomer");

        assertThat(service.addMember(SAM, id, newcomer, BankRole.MEMBER).errorOrThrow())
                .isEqualTo(BankError.NO_PERMISSION);
        assertThat(service.addMember(KAI, id, SAM, BankRole.MEMBER).errorOrThrow())
                .isEqualTo(BankError.ALREADY_MEMBER);
        assertThat(service.removeMember(KAI, id, ALEX).errorOrThrow()).isEqualTo(BankError.CANNOT_REMOVE_LEADER);
        assertThat(service.removeMember(KAI, id, newcomer).errorOrThrow()).isEqualTo(BankError.NOT_MEMBER);

        assertThat(service.removeMember(KAI, id, SAM).isOk()).isTrue();
        wallets.set(SAM, dollars("10"));
        assertThat(service.deposit(SAM, id, dollars("10")).errorOrThrow()).isEqualTo(BankError.NO_PERMISSION);
    }

    @Test
    void interestFollowsTheHighestTierTheBalanceReaches() {
        BankInterestPolicy policy = new BankInterestPolicy(
                true,
                Duration.ofHours(24),
                List.of(
                        new BankInterestPolicy.Tier(new BigDecimal("10000"), new BigDecimal("2")),
                        new BankInterestPolicy.Tier(new BigDecimal("100"), new BigDecimal("1"))));

        assertThat(policy.interestOn(dollars("99.99")).isZero()).isTrue();
        assertThat(policy.interestOn(dollars("500")).amount()).isEqualByComparingTo("5");
        assertThat(policy.interestOn(dollars("10000")).amount()).isEqualByComparingTo("200");
        assertThat(BankInterestPolicy.disabled().interestOn(dollars("10000")).isZero())
                .isTrue();
    }

    /** Bank rows whose balance changes in the same step as the wallet leg, or not at all. */
    private final class Banks implements BankRepository {

        private final Map<String, SharedBank> byId = new HashMap<>();

        @Override
        public Optional<SharedBank> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void save(SharedBank bank) {
            byId.put(bank.id(), bank);
        }

        @Override
        public void delete(String id) {
            byId.remove(id);
        }

        @Override
        public List<String> findBankIdsForPlayer(UUID uuid) {
            return byId.values().stream()
                    .filter(bank -> bank.members().stream()
                            .anyMatch(member -> member.player().uuid().equals(uuid)))
                    .map(SharedBank::id)
                    .toList();
        }

        @Override
        public List<SharedBank> findAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public void creditBank(String bankId, Money interest) {
            SharedBank bank = findById(bankId).orElseThrow();
            byId.put(bankId, bank.withBalance(bank.balance().plus(interest)));
        }

        @Override
        public Result<Unit, BankError> deposit(String bankId, PlayerRef player, Money amount) {
            SharedBank bank = byId.get(bankId);
            if (bank == null) {
                return Result.err(BankError.NOT_FOUND);
            }
            if (wallets.debit(player, amount).isErr()) {
                return Result.err(BankError.INSUFFICIENT_FUNDS);
            }
            byId.put(bankId, bank.withBalance(bank.balance().plus(amount)));
            return Result.ok();
        }

        @Override
        public Result<Unit, BankError> withdraw(String bankId, PlayerRef player, Money amount) {
            SharedBank bank = byId.get(bankId);
            if (bank == null || bank.balance().isLessThan(amount)) {
                return Result.err(BankError.INSUFFICIENT_BANK_FUNDS);
            }
            byId.put(bankId, bank.withBalance(bank.balance().minus(amount)));
            wallets.credit(player, amount);
            return Result.ok();
        }
    }

    /** The transfer lines the bank writes, as {@code from>to:amount:reason}. */
    private static final class History implements TransactionHistory {

        private final List<String> lines = new ArrayList<>();

        @Override
        public void recordTransfer(String fromId, String toId, Money amount, EconomyReason reason, long at) {
            lines.add(fromId + ">" + toId + ":" + amount.amount().toPlainString() + ":" + reason);
        }

        @Override
        public List<HistoryRecord> queryTransactions(UUID playerUuid, int limit, int offset) {
            return List.of();
        }

        @Override
        public List<HistoryRecord> queryGlobalTransactions(int limit, int offset) {
            return List.of();
        }

        @Override
        public void recordCredit(String ownerId, Money amount, EconomyReason reason, long at) {}

        @Override
        public void recordDebit(String ownerId, Money amount, EconomyReason reason, long at) {}

        @Override
        public List<HistoryRecord> queryBankTransactions(String bankId, int limit, int offset) {
            return List.of();
        }

        @Override
        public void flush() {}
    }
}
