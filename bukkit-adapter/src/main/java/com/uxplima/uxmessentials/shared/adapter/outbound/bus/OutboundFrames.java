package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.shared.network.NetworkMessage.MessageType;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The frames waiting for a player to carry them over plugin messaging, oldest first, held to a fixed number so a
 * backend nobody joins does not pin memory.
 *
 * <p>When the queue is full a frame has to go, chosen by what losing it costs:
 *
 * <ol>
 *   <li>A heartbeat first. A newer heartbeat says everything an older one did, so a queued heartbeat is replaced by
 *       the next one rather than kept beside it, and none is worth an announced change.
 *   <li>Then an ordinary change. A peer that misses one shows an old warp, home or hologram until its cache refreshes.
 *   <li>A protected change last: a balance or a vault, which a peer must not show stale while the player can spend
 *       or take from it; a ban, mute, jail or freeze, which must hold on every backend; and a vanish, whose loss can
 *       show a hidden player. One goes only when every frame waiting is protected.
 * </ol>
 *
 * <p>Within one kind the oldest frame goes, and a frame that arrives at a full queue holding nothing it outranks is
 * itself the one dropped. Every drop of a change is reported through {@link DroppedFrameReport}; a heartbeat is not,
 * since the next one carries all it said, and reporting it would repeat once a minute on a backend that stays empty.
 *
 * <p>Frames are queued from any thread and drained on the global thread, so every method holds the queue's monitor.
 */
@NullMarked
final class OutboundFrames {

    /** The frames whose loss leaves a peer wrong in a way that matters before its cache refreshes. */
    private static final Set<MessageType> PROTECTED = EnumSet.of(
            MessageType.BALANCE_CHANGED,
            MessageType.VAULT_CHANGED,
            MessageType.BAN_CHANGED,
            MessageType.IP_BAN_CHANGED,
            MessageType.MUTE_CHANGED,
            MessageType.JAIL_CHANGED,
            MessageType.FREEZE_CHANGED,
            MessageType.FREEZE_RESET,
            MessageType.VANISH_STATE_CHANGED);

    /** What losing a frame costs, cheapest first. */
    enum Weight {
        HEARTBEAT,
        ORDINARY,
        PROTECTED
    }

    /** A queued frame and its weight; a class, not a record, since the bytes are compared by identity only. */
    private static final class Queued {

        private final byte[] frame;
        private final Weight weight;

        Queued(byte[] frame, Weight weight) {
            this.frame = frame;
            this.weight = weight;
        }

        byte[] frame() {
            return frame;
        }

        Weight weight() {
            return weight;
        }
    }

    private final int capacity;
    private final DroppedFrameReport report;
    private final Deque<Queued> frames = new ArrayDeque<>();

    OutboundFrames(int capacity, DroppedFrameReport report) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        this.report = Objects.requireNonNull(report, "report");
    }

    /** Queue {@code frame}, making room by the rules above when the queue is full. */
    synchronized void add(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        Weight weight = weightOf(frame);
        if (weight == Weight.HEARTBEAT) {
            frames.removeIf(queued -> queued.weight() == Weight.HEARTBEAT);
        }
        if (frames.size() >= capacity) {
            Weight cheapest = cheapestQueued();
            if (weight.compareTo(cheapest) < 0) {
                dropped(frame, weight);
                return;
            }
            dropOldest(cheapest);
        }
        frames.addLast(new Queued(frame, weight));
    }

    /** The oldest frame, taken off the queue, or null when none is waiting. */
    synchronized byte @Nullable [] poll() {
        Queued oldest = frames.pollFirst();
        return oldest == null ? null : oldest.frame();
    }

    /** Forget every frame waiting; a stopped carrier sends none of them. */
    synchronized void clear() {
        frames.clear();
    }

    private Weight cheapestQueued() {
        Weight cheapest = Weight.PROTECTED;
        for (Queued queued : frames) {
            if (queued.weight().compareTo(cheapest) < 0) {
                cheapest = queued.weight();
            }
        }
        return cheapest;
    }

    private void dropOldest(Weight weight) {
        for (Iterator<Queued> each = frames.iterator(); each.hasNext(); ) {
            Queued queued = each.next();
            if (queued.weight() == weight) {
                each.remove();
                dropped(queued.frame(), weight);
                return;
            }
        }
    }

    private void dropped(byte[] frame, Weight weight) {
        if (weight != Weight.HEARTBEAT) {
            report.dropped(typeOf(frame), capacity);
        }
    }

    private static Weight weightOf(byte[] frame) {
        if (NetworkMessageCodec.isServerPing(frame)) {
            return Weight.HEARTBEAT;
        }
        for (MessageType type : PROTECTED) {
            if (NetworkMessageCodec.isType(frame, type)) {
                return Weight.PROTECTED;
            }
        }
        return Weight.ORDINARY;
    }

    /** The frame's type for the report, read from its header. */
    private static String typeOf(byte[] frame) {
        for (MessageType type : MessageType.values()) {
            if (NetworkMessageCodec.isType(frame, type)) {
                return type.name();
            }
        }
        return "unknown";
    }
}
