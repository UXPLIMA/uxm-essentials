package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.jspecify.annotations.NullMarked;

/**
 * Which player's connection carried each proxy connection frame, and when, taken on that player's own thread as the
 * frame arrived. The frame itself goes on through the signature check and the bus to its listener, later and on
 * another thread; the listener takes the receipt by the frame's id to learn who carried it and whether it arrived
 * before or after something else that happened on that player's thread, such as their join.
 *
 * <p>Only the most recent receipts are kept: one nobody takes (a frame whose signature failed) is dropped once newer
 * ones push it out.
 */
@NullMarked
public final class CarrierReceipts {

    /** How many receipts wait to be taken at most; far more than the joins of a few seconds. */
    static final int KEPT = 256;

    private final LongSupplier clock;
    private final Map<UUID, Receipt> byFrame = new LinkedHashMap<>(KEPT, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Receipt> eldest) {
            return size() > KEPT;
        }
    };

    /** Receipts timed by {@code clock}, a monotonic reading in nanoseconds. */
    public CarrierReceipts(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The frame {@code frameId} arrived over {@code carrier}'s connection, now. Call it on the carrier's thread. */
    public void received(UUID frameId, UUID carrier) {
        Objects.requireNonNull(frameId, "frameId");
        Objects.requireNonNull(carrier, "carrier");
        Receipt receipt = new Receipt(carrier, clock.getAsLong());
        synchronized (byFrame) {
            byFrame.put(frameId, receipt);
        }
    }

    /** The receipt of the frame {@code frameId}, taken once; empty when no connection carried it. */
    public Optional<Receipt> take(UUID frameId) {
        Objects.requireNonNull(frameId, "frameId");
        synchronized (byFrame) {
            return Optional.ofNullable(byFrame.remove(frameId));
        }
    }

    /** The current reading of the clock receipts are timed by, for a stamp to compare one against. */
    public long now() {
        return clock.getAsLong();
    }

    /**
     * One frame's arrival.
     *
     * @param carrier the player whose connection carried it
     * @param receivedNanos when it arrived, on the clock of these receipts
     */
    public record Receipt(UUID carrier, long receivedNanos) {

        public Receipt {
            Objects.requireNonNull(carrier, "carrier");
        }
    }
}
