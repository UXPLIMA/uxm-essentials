package com.uxplima.uxmessentials.vaults.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.inbound.window.WindowOutcome;
import com.uxplima.uxmessentials.vaults.application.VaultOpenFee;
import org.jspecify.annotations.NullMarked;

/** Ties a vault open's fee to whether its window came up: spent when it showed, given back once when it did not. */
@NullMarked
final class FeeOutcome {

    private FeeOutcome() {}

    /**
     * The outcome of one open that took {@code fee}. Shown spends the fee and runs {@code shown}; refused hands the
     * fee's deposit to {@code refund}, which runs it off the tick, and runs {@code refused}. A fee already given back
     * elsewhere rules the window out, so it can only be refused, and its deposit is not handed out again.
     */
    static WindowOutcome of(VaultOpenFee fee, Runnable shown, Runnable refused, Consumer<BooleanSupplier> refund) {
        Objects.requireNonNull(fee, "fee");
        Objects.requireNonNull(shown, "shown");
        Objects.requireNonNull(refused, "refused");
        Objects.requireNonNull(refund, "refund");
        return new WindowOutcome(
                () -> {
                    if (fee.spend()) {
                        shown.run();
                    }
                },
                () -> {
                    fee.giveBack().ifPresent(refund);
                    refused.run();
                },
                fee::givenBack);
    }
}
