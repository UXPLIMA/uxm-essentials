package com.uxplima.uxmessentials.economy.adapter.outbound.backend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.adapter.outbound.EntityHandoff;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Precision;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Paper experience as a currency, native to the server with no plugin behind it. Balance is the player's total
 * experience points, computed from level + within-level progress (the same vanilla curve the client shows) rather
 * than the unreliable {@code getTotalExperience()} counter, which does not account for spent levels. A credit or
 * debit re-derives the level and progress for the new total.
 *
 * <p>Experience lives on the online {@link Player}: this is the only shipped backend that cannot be written while
 * its owner is offline, so {@link #worksOffline()} is false and a write against an offline owner returns
 * {@link TransferError#PLAYER_OFFLINE}. Points are whole, so an amount is rounded to whole units at the boundary.
 *
 * <p>Behind {@code EconomyProvider} every provider call is off-tick (an economy call is treated as DB I/O), but a
 * {@link Player} is owned by its region/entity thread and mutating it from any other thread is unsupported on Paper
 * and a hard violation on Folia. So each operation hops onto the owner's entity thread through the injected
 * {@link Scheduler}, does its read and write there in one hop, a check-and-set is never split across threads, and
 * bridges the result back through a bounded await. A call already on a tick thread runs inline to avoid blocking
 * itself. A player who leaves before the hop runs degrades to {@link TransferError#PLAYER_OFFLINE} (or a zero
 * balance). A hop that stalls past the bound is abandoned so it never runs late: a write then reports
 * {@link TransferError#PLAYER_OFFLINE}, which is true since nothing changed, and a balance read throws, since the
 * balance is unknown and a caller that took it for zero would set it from a wrong figure.
 */
public final class ExpCurrencyBackend implements CurrencyBackend {

    /** A hop must never wedge the call that made it; a stalled hop degrades to the offline fallback after this. */
    private static final Duration HOP_TIMEOUT = Duration.ofSeconds(2);

    private final Server server;
    private final Scheduler scheduler;
    private final AtomicBoolean warned = new AtomicBoolean();
    private final EntityHandoff.Outcomes outcomes;

    public ExpCurrencyBackend(Server server, Scheduler scheduler, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        this.outcomes = new EntityHandoff.Outcomes() {
            @Override
            public void timedOut() {
                if (warned.compareAndSet(false, true)) {
                    log.warn("event=currency_backend_failed id=exp reason=hop_timeout");
                }
            }

            @Override
            public void failed(Throwable failure) {
                log.error("event=currency_backend_failed id=exp", failure);
            }
        };
    }

    @Override
    public String id() {
        return "exp";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean worksOffline() {
        return false;
    }

    @Override
    public boolean atomicDebit() {
        return false;
    }

    @Override
    public Precision precision() {
        return Precision.INTEGRAL;
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(currency, "currency");
        Optional<Money> read = onOwnerThread(
                owner,
                () -> Optional.of(readBalance(owner, currency)),
                Optional.of(Money.zero(currency)),
                Optional.empty());
        return read.orElseThrow(() -> new IllegalStateException("exp balance of " + owner.uuid() + " did not answer"));
    }

    private Money readBalance(PlayerRef owner, Currency currency) {
        Player player = server.getPlayer(owner.uuid());
        return player == null ? Money.zero(currency) : Money.of(currency, BigDecimal.valueOf(readTotal(player)));
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        Result<Unit, TransferError> offline = Result.err(TransferError.PLAYER_OFFLINE);
        return onOwnerThread(owner, () -> applyCredit(owner, amount), offline, offline);
    }

    private Result<Unit, TransferError> applyCredit(PlayerRef owner, Money amount) {
        Player player = server.getPlayer(owner.uuid());
        if (player == null) {
            return Result.err(TransferError.PLAYER_OFFLINE);
        }
        applyTotal(player, readTotal(player) + whole(amount));
        return Result.ok();
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(amount, "amount");
        Result<Unit, TransferError> offline = Result.err(TransferError.PLAYER_OFFLINE);
        return onOwnerThread(owner, () -> applyDebit(owner, amount), offline, offline);
    }

    private Result<Unit, TransferError> applyDebit(PlayerRef owner, Money amount) {
        Player player = server.getPlayer(owner.uuid());
        if (player == null) {
            return Result.err(TransferError.PLAYER_OFFLINE);
        }
        int total = readTotal(player);
        int wanted = whole(amount);
        if (total < wanted) {
            return Result.err(TransferError.INSUFFICIENT_FUNDS);
        }
        applyTotal(player, total - wanted);
        return Result.ok();
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        Objects.requireNonNull(currency, "currency");
        return List.of();
    }

    /**
     * Run {@code work} on the owner's entity thread and return its result. Already on a tick thread the work runs
     * inline: scheduling then blocking that same thread would deadlock. Otherwise the wait and the hop race through
     * {@link EntityHandoff}: a retired owner (left first) yields {@code offline}; a hop that has not started within
     * the bound is abandoned, never runs, and yields {@code unknown}, the stall logged once so a persistently wedged
     * hop cannot spam the log; a hop that started is waited for, since its change lands either way.
     */
    private <T> T onOwnerThread(PlayerRef owner, Supplier<T> work, T offline, T unknown) {
        // Folia splits the tick across region threads, so "am I on a tick thread" is the wrong question, a caller
        // on one region's thread must still hop to reach an entity owned by another. The Scheduler port answers the
        // right one.
        if (scheduler.ownsEntity(owner)) {
            return work.get();
        }
        EntityHandoff<T> hop = new EntityHandoff<>();
        scheduler.onEntity(owner, () -> hop.run(work), () -> hop.retire(offline));
        return hop.await(HOP_TIMEOUT, unknown, outcomes);
    }

    private static int whole(Money amount) {
        return amount.amount().setScale(0, RoundingMode.HALF_UP).intValueExact();
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
