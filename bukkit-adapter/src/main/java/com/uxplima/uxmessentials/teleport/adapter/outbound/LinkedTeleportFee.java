package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.MoneyFormat;
import com.uxplima.uxmessentials.economy.application.UncountedMoney;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link TeleportFee} bound to the resolved {@link EconomyProvider}, resolving its backing provider and
 * currency lazily on each call. The economy module is enabled after teleport, so at teleport-wire time the
 * provider and currency are not yet available; the composition root captures them onto its cross-context
 * links and hands their suppliers here. While economy is disabled (the supplier stays null) every request is
 * free: {@link #canAfford} is always {@code true} and {@link #charge} and {@link #refund} succeed without moving
 * money, so the teleport context never branches on economy presence and the wiring stays order-independent.
 *
 * <p>{@code canAfford} is a plain balance read taken on the command thread before the search. {@code charge} is
 * the guarded single-sided debit and {@code refund} the matching credit; both are reached from a region thread
 * (the end of the warmup, a missed hop), so they run on the async scheduler and complete their stage from there,
 * keeping the database write off the tick. A charge or refund that does not go through is logged, since the
 * player sees only the refusal line and an operator may need to settle a balance by hand. Both stages always
 * complete normally: a provider that throws answers {@code RTP_CHARGE_FAILED} or {@code RTP_REFUND_FAILED}.
 */
@NullMarked
public final class LinkedTeleportFee implements TeleportFee {

    private final Supplier<@Nullable EconomyProvider> provider;
    private final Supplier<@Nullable Currency> currency;
    private final Scheduler scheduler;
    private final Logger log;
    private final Optional<ChargeReceipts> receipts;

    public LinkedTeleportFee(
            Supplier<@Nullable EconomyProvider> provider,
            Supplier<@Nullable Currency> currency,
            Scheduler scheduler,
            Logger log,
            Optional<ChargeReceipts> receipts) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
    }

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        EconomyProvider live = provider.get();
        Currency unit = currency.get();
        if (live == null || unit == null) {
            return true; // economy disabled. The cost is free
        }
        return !live.balance(who, unit).isLessThan(Money.of(unit, amount));
    }

    @Override
    public CompletionStage<Result<Unit, TeleportError>> charge(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        EconomyProvider live = provider.get();
        Currency unit = currency.get();
        if (live == null || unit == null) {
            return CompletableFuture.completedFuture(Result.ok()); // economy disabled: nothing to withdraw
        }
        CompletableFuture<Result<Unit, TeleportError>> outcome = new CompletableFuture<>();
        try {
            scheduler.async(() -> outcome.complete(debit(live, unit, who, amount)));
        } catch (RuntimeException refused) {
            // The async lane refuses work once the plugin is disabling; nothing was taken, so the teleport is simply
            // off.
            log.warn("rtp charge for {} not taken: {}", who.name(), String.valueOf(refused.getMessage()));
            outcome.complete(Result.err(TeleportError.RTP_CHARGE_FAILED));
        }
        return outcome;
    }

    @Override
    public CompletionStage<Result<Unit, TeleportError>> refund(PlayerRef who, BigDecimal amount) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        EconomyProvider live = provider.get();
        Currency unit = currency.get();
        if (live == null || unit == null) {
            return CompletableFuture.completedFuture(Result.ok()); // economy disabled: nothing was taken
        }
        CompletableFuture<Result<Unit, TeleportError>> outcome = new CompletableFuture<>();
        try {
            scheduler.async(() -> outcome.complete(credit(live, unit, who, amount)));
        } catch (RuntimeException refused) {
            // The async lane refuses work once the plugin is disabling, but the money is still owed, and the server
            // has stopped ticking by then, so the credit runs here rather than being dropped.
            outcome.complete(credit(live, unit, who, amount));
        }
        return outcome;
    }

    @Override
    public String format(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        Currency unit = currency.get();
        return unit == null ? amount.toPlainString() : MoneyFormat.withSymbol(Money.of(unit, amount));
    }

    private Result<Unit, TeleportError> debit(EconomyProvider live, Currency unit, PlayerRef who, BigDecimal amount) {
        Money charge = Money.of(unit, amount);
        try {
            Result<Unit, TransferError> debited = live.debit(who, charge);
            if (debited.isErr()) {
                TransferError reason = debited.errorOrThrow();
                log.warn("rtp charge of {} refused for {}: {}", amount.toPlainString(), who.name(), reason);
                return Result.err(
                        reason == TransferError.INSUFFICIENT_FUNDS || reason == TransferError.BELOW_MINIMUM
                                ? TeleportError.RTP_CANT_AFFORD
                                : TeleportError.RTP_CHARGE_FAILED);
            }
        } catch (RuntimeException debitFailure) {
            // The stage must complete either way, or the player is left standing with no teleport and no answer.
            log.error("rtp charge of " + amount.toPlainString() + " failed for " + who.name(), debitFailure);
            return Result.err(TeleportError.RTP_CHARGE_FAILED);
        }
        try {
            receipts.ifPresent(receipt -> receipt.charged(who, charge, EconomyMessageKey.CHARGE_TELEPORT));
        } catch (RuntimeException receiptFailure) {
            // The money has left, so the charge stands; only the notice about it was lost.
            log.error("rtp charge receipt for " + who.name() + " could not be sent", receiptFailure);
        }
        return Result.ok();
    }

    private Result<Unit, TeleportError> credit(EconomyProvider live, Currency unit, PlayerRef who, BigDecimal amount) {
        try {
            // A refund gives back what was spent, so it is not counted as earned.
            Result<Unit, TransferError> credited = UncountedMoney.call(() -> live.credit(who, Money.of(unit, amount)));
            if (credited.isOk()) {
                return Result.ok();
            }
            log.warn(
                    "rtp refund of {} refused for {}: {}", amount.toPlainString(), who.name(), credited.errorOrThrow());
        } catch (RuntimeException creditFailure) {
            log.error("rtp refund of " + amount.toPlainString() + " failed for " + who.name(), creditFailure);
        }
        return Result.err(TeleportError.RTP_REFUND_FAILED);
    }
}
