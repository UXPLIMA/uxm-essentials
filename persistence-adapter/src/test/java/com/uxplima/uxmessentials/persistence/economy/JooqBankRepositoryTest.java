package com.uxplima.uxmessentials.persistence.economy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.BankError;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankMember;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankRole;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Shared banks through the real migrations: money moves between wallet and bank whole, or not at all. */
@EveryBackend
class JooqBankRepositoryTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Alex");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a2"), "Sam");
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).max(new BigDecimal("1000")).build();

    private JooqBankRepository banks;
    private JooqWalletRepository wallets;

    @BeforeEach
    void open(TestDatabase database) {
        CurrencyRegistry currencies = CurrencyRegistry.of(List.of(COINS), COINS.id());
        Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
        banks = new JooqBankRepository(database.dsl(), currencies, clock);
        wallets = new JooqWalletRepository(database.dsl(), currencies, clock);
        banks.save(new SharedBank(
                "guild",
                "The Guild",
                coins("100"),
                ALEX,
                List.of(new BankMember(ALEX, BankRole.LEADER), new BankMember(SAM, BankRole.MEMBER)),
                1_000L));
    }

    @Test
    void aSavedBankIsReadBackWithItsRoster() {
        SharedBank guild = banks.findById("guild").orElseThrow();

        assertThat(guild.name()).isEqualTo("The Guild");
        assertThat(guild.balance().amount()).isEqualByComparingTo("100");
        assertThat(guild.members())
                .extracting(member -> member.player().uuid() + ":" + member.role())
                .containsExactlyInAnyOrder(ALEX.uuid() + ":LEADER", SAM.uuid() + ":MEMBER");
        assertThat(banks.findBankIdsForPlayer(SAM.uuid())).containsExactly("guild");
    }

    @Test
    void aSaveRewritesTheRosterAndTheBalance() {
        SharedBank guild = banks.findById("guild").orElseThrow();

        banks.save(guild.withoutMember(SAM).withBalance(coins("60")));

        SharedBank saved = banks.findById("guild").orElseThrow();
        assertThat(saved.members()).extracting(BankMember::player).containsExactly(ALEX);
        assertThat(saved.balance().amount()).isEqualByComparingTo("60");
        assertThat(banks.findBankIdsForPlayer(SAM.uuid())).isEmpty();
    }

    @Test
    void aDepositMovesMoneyFromTheWalletIntoTheBank() {
        wallets.credit(SAM, coins("40"));

        assertThat(banks.deposit("guild", SAM, coins("15")).isOk()).isTrue();

        assertThat(bankBalance()).isEqualByComparingTo("115");
        assertThat(walletBalance(SAM)).isEqualByComparingTo("25");
    }

    @Test
    void aDepositIntoABankThatIsGoneLeavesTheWalletWhole() {
        wallets.credit(SAM, coins("40"));
        banks.delete("guild");

        assertThat(banks.deposit("guild", SAM, coins("15")).errorOrThrow()).isEqualTo(BankError.NOT_FOUND);

        assertThat(walletBalance(SAM)).isEqualByComparingTo("40");
        assertThat(banks.findById("guild")).isEmpty();
    }

    @Test
    void aWithdrawalTheBankCannotCoverPaysNothing() {
        assertThat(banks.withdraw("guild", SAM, coins("100.01")).errorOrThrow())
                .isEqualTo(BankError.INSUFFICIENT_BANK_FUNDS);
        assertThat(banks.withdraw("guild", SAM, coins("30")).isOk()).isTrue();

        assertThat(bankBalance()).isEqualByComparingTo("70");
        assertThat(walletBalance(SAM)).isEqualByComparingTo("30");
    }

    @Test
    void interestIsAddedToTheBalance() {
        banks.creditBank("guild", coins("2.5"));

        assertThat(bankBalance()).isEqualByComparingTo("102.5");
        assertThat(banks.findAll()).extracting(SharedBank::id).containsExactly("guild");
    }

    private BigDecimal bankBalance() {
        return banks.findById("guild").orElseThrow().balance().amount();
    }

    private BigDecimal walletBalance(PlayerRef owner) {
        return wallets.findByOwner(owner).orElseThrow().balanceOf(COINS).amount();
    }

    private static Money coins(String amount) {
        return Money.of(COINS, new BigDecimal(amount));
    }
}
