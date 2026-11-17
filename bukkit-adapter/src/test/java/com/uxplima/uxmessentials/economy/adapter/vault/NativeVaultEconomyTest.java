package com.uxplima.uxmessentials.economy.adapter.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A plugin paying through Vault gets Vault's failure answer when the ledger cannot complete the change, never an
 * exception thrown into its own code on its own thread, and the console hears of it without being flooded.
 */
class NativeVaultEconomyTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();

    private ServerMock server;
    private PlayerMock alex;
    private final List<String> errors = new ArrayList<>();
    private final AtomicLong nanos = new AtomicLong();
    private final Unreachable unreachable = new Unreachable();

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        alex = server.addPlayer("Alex");
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void aDepositTheLedgerCouldNotCompleteIsAFailedResponse() {
        NativeVaultEconomy vault = vault();

        EconomyResponse response = vault.depositPlayer(alex, 25);

        assertThat(response.type).isEqualTo(ResponseType.FAILURE);
        assertThat(response.balance).isEqualTo(10d);
    }

    @Test
    void aWithdrawalTheLedgerCouldNotCompleteIsAFailedResponse() {
        NativeVaultEconomy vault = vault();

        EconomyResponse response = vault.withdrawPlayer(alex, 5);

        assertThat(response.type).isEqualTo(ResponseType.FAILURE);
        assertThat(response.balance).isEqualTo(10d);
    }

    @Test
    void aFailedWriteIsLoggedAndABurstOfThemIsLoggedOnceAMinute() {
        NativeVaultEconomy vault = vault();

        vault.depositPlayer(alex, 25);
        vault.withdrawPlayer(alex, 5);
        vault.depositPlayer(alex, 1);

        assertThat(errors).singleElement().asString().contains("event=vault_write_failed", "op=deposit");
        nanos.addAndGet(Duration.ofMinutes(1).toNanos());
        vault.depositPlayer(alex, 1);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(1)).contains("suppressed=2");
    }

    @Test
    void aBalanceThatDidNotAnswerIsAFailedResponseAndNeverAnException() {
        unreachable.balancesFail = true;
        NativeVaultEconomy vault = vault();

        assertThat(vault.getBalance(alex)).isZero();
        assertThat(vault.has(alex, 1)).isFalse();
        assertThat(vault.withdrawPlayer(alex, 5).type).isEqualTo(ResponseType.FAILURE);
        assertThat(vault.depositPlayer(alex, 5).type).isEqualTo(ResponseType.FAILURE);
        assertThat(errors).singleElement().asString().contains("op=balance");
    }

    @Test
    void aFailedWriteDoesNotClaimNothingMoved() {
        EconomyResponse response = vault().depositPlayer(alex, 25);

        assertThat(response.errorMessage).doesNotContain("nothing moved");
    }

    private NativeVaultEconomy vault() {
        return new NativeVaultEconomy(unreachable, COINS, server, new Recording(errors), nanos::get);
    }

    /** Keeps the error lines. */
    private record Recording(List<String> errors) implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            errors.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }

    /** Reads still work; every write fails the way a ledger that lost its connection does. */
    private static final class Unreachable implements EconomyProvider {

        boolean balancesFail;

        @Override
        public boolean hasAccount(PlayerRef owner, Currency currency) {
            return true;
        }

        @Override
        public void ensureAccount(PlayerRef owner, Currency currency) {}

        @Override
        public Money balance(PlayerRef owner, Currency currency) {
            if (balancesFail) {
                throw new IllegalStateException("balance did not answer");
            }
            return Money.of(currency, BigDecimal.TEN);
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
            throw new IllegalStateException("connection lost");
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
            throw new IllegalStateException("connection lost");
        }

        @Override
        public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
            throw new IllegalStateException("connection lost");
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
}
