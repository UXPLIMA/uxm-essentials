package com.uxplima.uxmessentials.trade.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BoundedAwait;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeExperience;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the trade context's {@link TradeExperience} seam to a live Bukkit {@link Player}'s experience, so a trade's
 * staked experience moves without the trade context ever touching a Bukkit type. Balance is the player's total
 * experience points, computed from level plus within-level progress (the same vanilla curve the client shows) rather
 * than the unreliable {@code getTotalExperience()} counter, which does not account for spent levels; a credit or debit
 * re-derives the level and progress for the new total, the same approach the native experience currency backend uses.
 *
 * <p>Experience lives on the online player. A {@link Player} is owned by its region/entity thread and mutating it from
 * any other thread is unsupported on Paper and a hard violation on Folia, so each operation hops onto the owner's
 * entity thread through the injected {@link Scheduler}, does its read and write there in one hop (a check-and-set is
 * never split across threads), and bridges the result back through a bounded await. A call already on the owning tick
 * thread runs inline. A player who left mid-hop, or a hop that stalls past the bound, degrades to the offline fallback
 * (a zero read, a {@code false} withdraw, a dropped deposit) rather than wedging the caller.
 */
@NullMarked
public final class BukkitTradeExperience implements TradeExperience {

    /** A hop must never wedge the settle that made it; a stalled hop degrades to the offline fallback after this. */
    private static final Duration HOP_TIMEOUT = Duration.ofSeconds(2);

    private final Server server;
    private final Scheduler scheduler;
    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    public BukkitTradeExperience(Server server, Scheduler scheduler, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public long available(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return onOwnerThread(who, () -> readAvailable(who), 0L);
    }

    private long readAvailable(PlayerRef who) {
        Player player = server.getPlayer(who.uuid());
        return player == null ? 0L : readTotal(player);
    }

    @Override
    public boolean withdraw(PlayerRef who, long points) {
        Objects.requireNonNull(who, "who");
        if (points <= 0) {
            return true;
        }
        return onOwnerThreadFlag(who, () -> applyWithdraw(who, points));
    }

    private boolean applyWithdraw(PlayerRef who, long points) {
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return false;
        }
        int total = readTotal(player);
        if (total < points) {
            return false;
        }
        applyTotal(player, total - (int) points);
        return true;
    }

    @Override
    public void deposit(PlayerRef who, long points) {
        Objects.requireNonNull(who, "who");
        if (points <= 0) {
            return;
        }
        // Best-effort credit; the boolean result is ignored, an offline owner (or a stalled hop) simply drops it.
        onOwnerThreadFlag(who, () -> applyDeposit(who, points));
    }

    private boolean applyDeposit(PlayerRef who, long points) {
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return false;
        }
        applyTotal(player, readTotal(player) + (int) points);
        return true;
    }

    /**
     * Run {@code work} on the owner's entity thread and return its result. Already on that tick thread the work runs
     * inline, scheduling then blocking that same thread would deadlock. Otherwise the hop completes a future, awaited
     * with a hard bound; a retired owner (left mid-hop) or a stall yields {@code offline}, the stall logged once so a
     * persistently wedged hop cannot spam the log.
     */
    private <T> T onOwnerThread(PlayerRef owner, Supplier<T> work, T offline) {
        if (scheduler.ownsEntity(owner)) {
            return work.get();
        }
        CompletableFuture<T> hop = new CompletableFuture<>();
        scheduler.onEntity(owner, () -> hop.complete(work.get()), () -> hop.complete(offline));
        try {
            return BoundedAwait.get(hop, HOP_TIMEOUT, "trade experience hop");
        } catch (IllegalStateException stalled) {
            if (warned.compareAndSet(false, true)) {
                log.warn("event=trade_experience_hop_failed reason=hop_timeout");
            }
            return offline;
        }
    }

    /** As {@link #onOwnerThread} for a boolean result, degrading a retired owner or a stall to {@code false}. */
    private boolean onOwnerThreadFlag(PlayerRef owner, BooleanSupplier work) {
        return onOwnerThread(owner, work::getAsBoolean, Boolean.FALSE);
    }

    /** Total experience points for {@code player}, summed from the per-level costs up to the current level. */
    private static int readTotal(Player player) {
        int level = player.getLevel();
        int total = 0;
        for (int l = 0; l < level; l++) {
            total += expToNext(l);
        }
        return total + Math.round(player.getExp() * expToNext(level));
    }

    /** Re-derive the level and within-level progress for {@code total} points and write them back. */
    private static void applyTotal(Player player, int total) {
        int remaining = Math.max(0, total);
        int level = 0;
        // Bounded against absurd inputs; a real experience total never approaches this many levels.
        while (level < 1_000_000 && remaining >= expToNext(level)) {
            remaining -= expToNext(level);
            level++;
        }
        player.setLevel(level);
        player.setExp((float) remaining / expToNext(level));
    }

    /** The points needed to advance from {@code level} to the next, per the vanilla experience curve. */
    private static int expToNext(int level) {
        if (level >= 31) {
            return 9 * level - 158;
        }
        if (level >= 16) {
            return 5 * level - 38;
        }
        return 2 * level + 7;
    }
}
