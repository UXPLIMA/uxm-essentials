package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.Test;

class FakeCurrencyBackendTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Ada");
    private static final Currency CAPPED =
            Currency.builder(CurrencyId.of("coins")).max(new BigDecimal("5")).build();

    @Test
    void creditRejectsWhenTheResultingBalanceWouldExceedTheCurrencyMax() {
        FakeCurrencyBackend backend = new FakeCurrencyBackend("native");
        backend.seed(OWNER, new BigDecimal("4"));

        Result<Unit, TransferError> result = backend.credit(OWNER, Money.of(CAPPED, new BigDecimal("2")));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.BALANCE_MAX_EXCEEDED);
    }

    @Test
    void aRejectedCreditLeavesTheBalanceUntouched() {
        FakeCurrencyBackend backend = new FakeCurrencyBackend("native");
        backend.seed(OWNER, new BigDecimal("4"));

        backend.credit(OWNER, Money.of(CAPPED, new BigDecimal("2")));

        assertThat(backend.balance(OWNER, CAPPED).amount()).isEqualByComparingTo(new BigDecimal("4"));
    }
}
