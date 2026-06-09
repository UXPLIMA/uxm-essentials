package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.economy.domain.TransferError;
import org.jspecify.annotations.Nullable;

/**
 * Result of a currency exchange request.
 */
public record ExchangeOutcome(
        Status status, BigDecimal sourceAmount, BigDecimal targetAmount, @Nullable TransferError error) {
    public enum Status {
        SUCCESS,
        RATE_NOT_FOUND,
        INSUFFICIENT_FUNDS,
        LIMIT_EXCEEDED,
        FAILED,

        /**
         * The source debit applied but the target credit failed AND the compensating credit (the rollback)
         * also failed — so the source amount was debited with nothing credited back. This is a money-losing
         * fault the operator must correct, surfaced as its own status so the adapter can log it (the core
         * layer cannot reach SLF4J).
         */
        ROLLBACK_FAILED
    }

    public static ExchangeOutcome success(BigDecimal sourceAmount, BigDecimal targetAmount) {
        return new ExchangeOutcome(Status.SUCCESS, sourceAmount, targetAmount, null);
    }

    public static ExchangeOutcome rateNotFound() {
        return new ExchangeOutcome(Status.RATE_NOT_FOUND, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    public static ExchangeOutcome insufficientFunds() {
        return new ExchangeOutcome(
                Status.INSUFFICIENT_FUNDS, BigDecimal.ZERO, BigDecimal.ZERO, TransferError.INSUFFICIENT_FUNDS);
    }

    public static ExchangeOutcome limitExceeded() {
        return new ExchangeOutcome(
                Status.LIMIT_EXCEEDED, BigDecimal.ZERO, BigDecimal.ZERO, TransferError.BALANCE_MAX_EXCEEDED);
    }

    public static ExchangeOutcome failed(TransferError error) {
        return new ExchangeOutcome(Status.FAILED, BigDecimal.ZERO, BigDecimal.ZERO, error);
    }

    /**
     * The debit applied but neither the target credit nor the compensating rollback did — the source
     * {@code debited} amount is lost until an operator corrects it. The error carried is the rollback's, so
     * the adapter can log exactly why the compensation failed.
     */
    public static ExchangeOutcome rollbackFailed(BigDecimal debited, TransferError rollbackError) {
        return new ExchangeOutcome(Status.ROLLBACK_FAILED, debited, BigDecimal.ZERO, rollbackError);
    }
}
