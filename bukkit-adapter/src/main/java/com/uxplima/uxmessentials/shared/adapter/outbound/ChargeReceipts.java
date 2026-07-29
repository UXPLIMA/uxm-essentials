package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.MoneyFormat;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Tells a player that a feature just took money from their wallet. Most charges here are a side effect of something
 * else the player asked for: they ran {@code /warp shop}, claimed a kit, teleported home. The money leaves silently,
 * the feature's own success message says nothing about it, and the only trace is a balance nobody was watching. That
 * is the same shape as an item that never arrives: from the player's side it is indistinguishable from being robbed.
 *
 * <p>The receipt is one sentence, {@code eco.charged}, with the amount and a label naming what the money went to
 * ("Paid 50 coins for a warp"). Each seam passes its own label key, so the sentence stays one catalog entry and the
 * per-feature wording stays translatable next to it. The amount goes through the same {@link MoneyFormat} every other
 * economy surface uses, so it reads exactly like the figure {@code /balance} prints.
 *
 * <p>A refused charge is not reported here: the feature already answers that with its own "you cannot afford" line,
 * and nothing left the wallet to explain. A zero or negative amount is likewise silent, since no money moved.
 */
@NullMarked
public final class ChargeReceipts {

    private final Messages messages;
    private final MessageSink sink;

    public ChargeReceipts(Messages messages, MessageSink sink) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /**
     * Report that {@code amount} was taken from {@code who} for the thing {@code what} names. Call this only after the
     * debit has actually succeeded: this is a receipt, not a promise.
     *
     * @param who the payer, and the locale the receipt is resolved in
     * @param amount what left the wallet, in the currency it was charged in
     * @param what the label key naming the feature the money went to
     */
    public void charged(PlayerRef who, Money amount, MessageKey what) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(what, "what");
        if (amount.amount().signum() <= 0) {
            return;
        }
        String label = messages.resolve(who, what, Map.of());
        sink.deliver(
                who,
                messages.resolve(
                        who,
                        EconomyMessageKey.CHARGED,
                        Map.of("amount", MoneyFormat.withSymbol(amount), "what", label)));
    }
}
