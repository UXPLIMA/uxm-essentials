package com.uxplima.uxmessentials.vaults.application;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.domain.VaultPayment;

/**
 * The open fee one open of a vault took, and what becomes of it. The fee buys a window in front of the player, so it
 * is spent once the window is showing and given back if the window never gets there: the open was refused by another
 * plugin, by the invsee edit guard, by a vault already open elsewhere, or the player left or stayed asleep first. Only
 * the first of the two counts, so a fee is never both kept and given back, and never given back twice.
 *
 * <p>The open is the one the fee was charged for, so a free open goes through the same two steps: the caller decides
 * the outcome in one place whatever the open cost.
 */
public final class VaultOpenFee {

    private enum State {
        PENDING,
        SPENT,
        GIVEN_BACK
    }

    private final Optional<VaultPayment> taken;
    private final BooleanSupplier deposit;
    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);

    VaultOpenFee(VaultCharge charge, PlayerRef payer, Optional<VaultPayment> taken) {
        Objects.requireNonNull(charge, "charge");
        Objects.requireNonNull(payer, "payer");
        this.taken = Objects.requireNonNull(taken, "taken");
        this.deposit = () -> charge.giveBack(payer, taken);
    }

    private VaultOpenFee() {
        this.taken = Optional.empty();
        this.deposit = () -> true;
    }

    /** An open that took nothing: a staff audit, a bypass, or a free fee. */
    public static VaultOpenFee free() {
        return new VaultOpenFee();
    }

    /** What the open took, in the currency it was taken in; empty when it took nothing. */
    public Optional<VaultPayment> taken() {
        return taken;
    }

    /** The window is in front of the player, so the fee stays spent. True when this call decided the open. */
    public boolean spend() {
        return state.compareAndSet(State.PENDING, State.SPENT);
    }

    /**
     * The window will never be shown, so the fee goes back. Returns the deposit to run, off the tick since it reaches
     * the economy, which answers whether the credit landed; empty when the open was already decided, so the deposit
     * is handed out at most once.
     */
    public Optional<BooleanSupplier> giveBack() {
        return state.compareAndSet(State.PENDING, State.GIVEN_BACK) ? Optional.of(deposit) : Optional.empty();
    }

    /** True once the fee was given back, after which the window must not open for free. */
    public boolean givenBack() {
        return state.get() == State.GIVEN_BACK;
    }
}
