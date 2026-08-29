package com.uxplima.uxmessentials.economy.adapter.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The outward Vault view of the native wallet: what a third-party plugin sees when it asks the
 * {@code ServicesManager} for an economy and pays a reward through it.
 */
class NativeVaultEconomyTest {

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();

    /** A wallet in a map, with the one guard the real ledger has: a debit never goes below zero. */
    private static final class FakeLedger implements EconomyProvider {

        private final Map<PlayerRef, BigDecimal> balances = new HashMap<>();

        /** Reads served. The real repository answers a miss from the database, so the count is not free. */
        private int reads;

        @Override
        public boolean hasAccount(PlayerRef owner, Currency currency) {
            return balances.containsKey(owner);
        }

        @Override
        public void ensureAccount(PlayerRef owner, Currency currency) {
            balances.putIfAbsent(owner, BigDecimal.ZERO);
        }

        @Override
        public Money balance(PlayerRef owner, Currency currency) {
            reads++;
            return Money.of(currency, balances.getOrDefault(owner, BigDecimal.ZERO));
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            // As the native ledger does: a credit opens the account, so the caller never has to ask first.
            ensureAccount(owner, amount.currency());
            balances.merge(owner, amount.amount(), BigDecimal::add);
            return Result.ok();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            BigDecimal held = balances.getOrDefault(owner, BigDecimal.ZERO);
            if (held.compareTo(amount.amount()) < 0) {
                return Result.err(TransferError.INSUFFICIENT_FUNDS);
            }
            balances.put(owner, held.subtract(amount.amount()));
            return Result.ok();
        }

        @Override
        public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw new UnsupportedOperationException("the Vault view never calls transfer");
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }

        @Override
        public Set<Currency> currencies() {
            return Set.of(COINS);
        }
    }

    private ServerMock server;
    private PlayerMock player;
    private FakeLedger ledger;
    private NativeVaultEconomy economy;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        ledger = new FakeLedger();
        economy = new NativeVaultEconomy(ledger, COINS, server);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a deposit reaches the wallet and reads back")
    void aDepositReachesTheWallet() {
        assertThat(economy.depositPlayer(player, 50).transactionSuccess()).isTrue();

        assertThat(economy.getBalance(player)).isEqualTo(50d);
        assertThat(economy.has(player, 50)).isTrue();
        assertThat(economy.has(player, 51)).isFalse();
    }

    @Test
    @DisplayName("a deposit opens the account, so a first-time reward is not lost")
    void aDepositOpensTheAccount() {
        assertThat(economy.hasAccount(player)).isFalse();

        economy.depositPlayer(player, 10);

        assertThat(economy.hasAccount(player)).isTrue();
    }

    @Test
    @DisplayName("a movement reads the wallet once, and answers Vault by arithmetic rather than reading again")
    void aMovementReadsTheWalletOnce() {
        // Vault is synchronous, so this runs on whichever thread the paying plugin holds, usually the tick
        // thread. A read after the write is the expensive one: the write drops that owner from the repository
        // cache, so it is a guaranteed database round trip. Reading before and adding costs nothing extra and
        // is exact, because a credit that succeeds moved precisely the amount asked for.
        economy.depositPlayer(player, 20);
        int afterFirst = ledger.reads;

        assertThat(economy.depositPlayer(player, 30).balance).isEqualTo(50d);
        assertThat(ledger.reads - afterFirst).isEqualTo(1);

        int beforeWithdrawal = ledger.reads;
        assertThat(economy.withdrawPlayer(player, 5).balance).isEqualTo(45d);
        assertThat(ledger.reads - beforeWithdrawal).isEqualTo(1);
    }

    @Test
    @DisplayName("a withdrawal past the balance is refused and takes nothing")
    void anOverdraftIsRefused() {
        economy.depositPlayer(player, 20);

        assertThat(economy.withdrawPlayer(player, 30).transactionSuccess()).isFalse();
        assertThat(economy.getBalance(player)).isEqualTo(20d);
    }

    @Test
    @DisplayName("a negative amount is refused rather than reversed into a gift")
    void aNegativeAmountIsRefused() {
        economy.depositPlayer(player, 20);

        assertThat(economy.depositPlayer(player, -5).transactionSuccess()).isFalse();
        assertThat(economy.withdrawPlayer(player, -5).transactionSuccess()).isFalse();
        assertThat(economy.getBalance(player)).isEqualTo(20d);
    }

    @Test
    @DisplayName("the world argument changes nothing: a balance is per account")
    void theWorldArgumentIsIgnored() {
        economy.depositPlayer(player, 15);

        assertThat(economy.getBalance(player, "world_nether")).isEqualTo(15d);
        assertThat(economy.has(player, "world_nether", 15)).isTrue();
    }

    @Test
    @DisplayName("a name nobody has seen is refused, never looked up")
    void anUncachedNameIsRefused() {
        assertThat(economy.getBalance("nobody")).isEqualTo(0d);
        assertThat(economy.hasAccount("nobody")).isFalse();
        assertThat(economy.depositPlayer("nobody", 10).transactionSuccess()).isFalse();
    }

    @Test
    @DisplayName("the currency is described the way the configuration describes it")
    void itDescribesTheConfiguredCurrency() {
        assertThat(economy.getName()).isEqualTo("uxmEssentials");
        assertThat(economy.isEnabled()).isTrue();
        assertThat(economy.fractionalDigits()).isEqualTo(2);
        assertThat(economy.currencyNamePlural()).isEqualTo("coins");
        assertThat(economy.currencyNameSingular()).isEqualTo("coins");
        assertThat(economy.format(12.5)).contains("$");
    }

    @Test
    @DisplayName("a named bank says it is not implemented rather than answering for a personal wallet")
    void banksAreNotImplemented() {
        assertThat(economy.hasBankSupport()).isFalse();
        assertThat(economy.getBanks()).isEmpty();
        assertThat(economy.bankBalance("guild").type).isEqualTo(ResponseType.NOT_IMPLEMENTED);
        assertThat(economy.bankDeposit("guild", 10).type).isEqualTo(ResponseType.NOT_IMPLEMENTED);
    }
}
