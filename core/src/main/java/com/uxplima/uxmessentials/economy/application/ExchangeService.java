package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.ExchangeRate;
import com.uxplima.uxmessentials.economy.domain.ExchangeRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Service to orchestrate money conversions for players.
 * Debits the source wallet, credits the target wallet, and rolls back the debit atomically on failures.
 */
public final class ExchangeService {

    private final EconomyProvider provider;
    private final ExchangeRegistry exchangeRegistry;

    public ExchangeService(EconomyProvider provider, ExchangeRegistry exchangeRegistry) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.exchangeRegistry = Objects.requireNonNull(exchangeRegistry, "exchangeRegistry");
    }

    public ExchangeRegistry registry() {
        return exchangeRegistry;
    }

    /**
     * Performs an exchange of currency for a player.
     * Debit the source wallet first, then credit the target. If target credit fails, the debit is rolled back.
     */
    public ExchangeOutcome exchange(
            PlayerRef player, BigDecimal sourceAmount, Currency sourceCurrency, Currency targetCurrency) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(sourceAmount, "sourceAmount");
        Objects.requireNonNull(sourceCurrency, "sourceCurrency");
        Objects.requireNonNull(targetCurrency, "targetCurrency");

        if (sourceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ExchangeOutcome.failed(TransferError.BELOW_MINIMUM);
        }

        Optional<ExchangeRate> rateOpt = exchangeRegistry.findRate(sourceCurrency.id(), targetCurrency.id());
        if (rateOpt.isEmpty()) {
            return ExchangeOutcome.rateNotFound();
        }

        ExchangeRate rate = rateOpt.get();
        Optional<BigDecimal> targetOpt = rate.calculateTargetAmount(sourceAmount, targetCurrency.precision());
        if (targetOpt.isEmpty()) {
            // The source is so small the target rounds to zero — refuse rather than debit for nothing.
            return ExchangeOutcome.failed(TransferError.BELOW_MINIMUM);
        }
        BigDecimal targetAmount = targetOpt.get();

        // Debit the source wallet
        Money debitMoney = Money.of(sourceCurrency, sourceAmount);
        Result<Unit, TransferError> debitResult = provider.debit(player, debitMoney);

        if (debitResult.isErr()) {
            TransferError err = debitResult.errorOrThrow();
            if (err == TransferError.INSUFFICIENT_FUNDS) {
                return ExchangeOutcome.insufficientFunds();
            }
            return ExchangeOutcome.failed(err);
        }

        // Credit the target wallet
        Money creditMoney = Money.of(targetCurrency, targetAmount);
        Result<Unit, TransferError> creditResult = provider.credit(player, creditMoney);

        if (creditResult.isErr()) {
            TransferError err = creditResult.errorOrThrow();
            // Rollback the debit since the credit failed (e.g., balance max exceeded)
            Result<Unit, TransferError> rollbackResult = provider.credit(player, debitMoney);
            if (rollbackResult.isErr()) {
                // The debit applied but neither the credit nor the rollback did: money was lost. Surface this
                // as its own outcome so the adapter logs it for operator correction (core cannot log itself).
                return ExchangeOutcome.rollbackFailed(sourceAmount, rollbackResult.errorOrThrow());
            }
            if (err == TransferError.BALANCE_MAX_EXCEEDED) {
                return ExchangeOutcome.limitExceeded();
            }
            return ExchangeOutcome.failed(err);
        }

        return ExchangeOutcome.success(sourceAmount, targetAmount);
    }
}
