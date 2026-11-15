package com.uxplima.uxmessentials.teleport.application.port;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;

/**
 * The narrow economy seam the teleport context owns so an {@code /rtp} cost can be charged <em>without</em>
 * a hard dependency on the economy context: an affordability check, a withdrawal and a refund, expressed in
 * teleport's own terms. The composition root supplies an adapter that bridges this to the resolved
 * {@code EconomyProvider}, and the teleport context never imports an economy type.
 *
 * <p>Soft coupling: the wired implementation resolves the provider lazily, so a cost is simply free while the
 * economy module is disabled. {@link #canAfford} gates the request before the safe-search runs, so a player who
 * cannot pay never triggers a search. {@link #charge} runs when the warmup ends and before the hop, and its
 * outcome decides whether the hop happens at all, because the balance can drop while the player stands still.
 * {@link #refund} gives the money back when the hop then fails, so a player never pays for a teleport that did
 * not happen.
 *
 * <p>The withdrawal and the refund write to the database, so both answer with a stage the adapter completes off
 * the tick thread; the caller continues from there.
 */
public interface TeleportFee {

    /** A no-op fee: every request is affordable, nothing is ever withdrawn, and a refund has nothing to return. */
    TeleportFee FREE = new TeleportFee() {
        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            return true;
        }

        @Override
        public CompletionStage<Result<Unit, TeleportError>> charge(PlayerRef who, BigDecimal amount) {
            return CompletableFuture.completedFuture(Result.ok());
        }

        @Override
        public CompletionStage<Result<Unit, TeleportError>> refund(PlayerRef who, BigDecimal amount) {
            return CompletableFuture.completedFuture(Result.ok());
        }

        @Override
        public String format(BigDecimal amount) {
            return amount.toPlainString();
        }
    };

    /** True when {@code who} can cover {@code amount} in the default currency. */
    boolean canAfford(PlayerRef who, BigDecimal amount);

    /**
     * Withdraw {@code amount} from {@code who}'s balance. Completes with {@code ok} once the money has left, with
     * {@link TeleportError#RTP_CANT_AFFORD} when the balance did not cover it, or with
     * {@link TeleportError#RTP_CHARGE_FAILED} when the economy itself failed. The stage always completes normally.
     */
    CompletionStage<Result<Unit, TeleportError>> charge(PlayerRef who, BigDecimal amount);

    /**
     * Give back an {@code amount} that {@link #charge} took for a teleport that never landed. Completes with
     * {@code ok} once the money is back, or with {@link TeleportError#RTP_REFUND_FAILED} when it could not be
     * returned. The stage always completes normally.
     */
    CompletionStage<Result<Unit, TeleportError>> refund(PlayerRef who, BigDecimal amount);

    /** {@code amount} written the way the currency writes it, so every fee line reads like {@code /balance}. */
    String format(BigDecimal amount);
}
