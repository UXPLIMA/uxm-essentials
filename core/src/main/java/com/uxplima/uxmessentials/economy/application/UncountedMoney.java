package com.uxplima.uxmessentials.economy.application;

import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.NullMarked;

/**
 * Money that moves but is neither earned nor spent: an admin give or take, a banknote written or cashed, a refund.
 * The earned and spent leaderboards leave it off.
 *
 * <p>A move made inside {@link #call} is published with {@code counted} false. The mark is a {@link ScopedValue}, so
 * it holds for the calls made on this thread inside the block and nowhere else, and the provider API needs no reason
 * parameter that every caller would have to pass. A transfer between two players is never counted either; the
 * provider marks those itself.
 */
@NullMarked
public final class UncountedMoney {

    private static final ScopedValue<Boolean> UNCOUNTED = ScopedValue.newInstance();

    private UncountedMoney() {}

    /** Run {@code move} with every change of money it makes marked as not counted. */
    public static <T> T call(Supplier<T> move) {
        Objects.requireNonNull(move, "move");
        return ScopedValue.where(UNCOUNTED, Boolean.TRUE).call(move::get);
    }

    /** Whether a move made here counts as earned or spent: true unless inside {@link #call}. */
    public static boolean counted() {
        return !UNCOUNTED.isBound();
    }
}
