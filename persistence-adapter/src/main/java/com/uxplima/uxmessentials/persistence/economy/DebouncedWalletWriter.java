package com.uxplima.uxmessentials.persistence.economy;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Coalesces rapid balance settles into the fewest {@code UPSERT}s ({@code docs/11-economy-integration.md}
 * §10.1, the zEssentials {@code PendingEconomyUpdate} pattern scoped to the settle path). Each
 * {@code (owner, currency)} keeps an {@link AtomicReference} of the latest intended balance; a burst of
 * settles overwrites that reference (last write wins) and the row is flushed once per debounce window by a
 * self-rescheduling {@link Scheduler#asyncAfter} task gated on {@link #running}.
 *
 * <p>The hard boundary that keeps this safe: <strong>the guarded transfer/withdraw path in §3 is never
 * debounced</strong>. A transactional debit must hit the database synchronously inside its transaction so the
 * {@code AMOUNT >= ?} check is authoritative; debouncing applies only to idempotent last-value-wins settles
 * (a recomputed balance, a starting-balance credit, a reset) where collapsing intermediate values loses
 * nothing. {@code enqueue} is therefore never on the {@code /pay} path. The queue drains synchronously on
 * {@link #stop()} so a clean shutdown never strands a pending balance.
 */
@NullMarked
public final class DebouncedWalletWriter {

    private final Map<WalletKey, Settle> pending = new ConcurrentHashMap<>();
    private final WalletRepository repo;
    private final Scheduler scheduler;
    private final Logger log;
    private final Duration window;
    private volatile boolean running;

    public DebouncedWalletWriter(WalletRepository repo, Scheduler scheduler, Logger log, Duration window) {
        this.repo = Objects.requireNonNull(repo, "repo");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.window = Objects.requireNonNull(window, "window");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("write-debounce window must be positive: " + window);
        }
    }

    /** Arm the flush loop. Called once on module start; idempotent so a reload re-arm is harmless. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        scheduleNext();
    }

    /**
     * Enqueue a last-write-wins settle of {@code (owner, currency)} to {@code latest}. Collapses with any
     * other settle for the same key before the next flush; never call this on the guarded transfer/withdraw
     * path (§3).
     */
    public void enqueue(PlayerRef owner, Money latest) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(latest, "latest");
        WalletKey key = WalletKey.of(owner, latest.currency());
        pending.computeIfAbsent(key, k -> new Settle(owner)).value.set(latest);
    }

    /** One {@code UPSERT} per dirty {@code (owner, currency)}, then clear. Runs off-tick on the flush loop. */
    public void flush() {
        for (Map.Entry<WalletKey, Settle> entry : pending.entrySet()) {
            Settle settle = entry.getValue();
            Money latest = settle.value.getAndSet(null);
            if (latest != null) {
                flushOne(entry.getKey(), settle.owner, latest);
            }
        }
    }

    /** Drain synchronously and stop the flush loop. Called on module stop and before a reload swap. */
    public void stop() {
        running = false;
        flush();
    }

    private void flushOne(WalletKey key, PlayerRef owner, Money latest) {
        try {
            repo.upsertBalance(owner, latest);
        } catch (RuntimeException cause) {
            // A transient DB outage must not lose the settle: re-arm it so the next window retries. A
            // durable write-ahead outbox covers a crash mid-window (docs/02-concurrency.md); here the
            // in-memory re-arm is enough for a recoverable blip.
            pending.computeIfAbsent(key, k -> new Settle(owner)).value.compareAndSet(null, latest);
            log.warn("deferred wallet settle for {} failed; will retry next window", owner.uuid());
        }
    }

    private void scheduleNext() {
        scheduler.asyncAfter(window, () -> {
            if (!running) {
                return;
            }
            flush();
            scheduleNext();
        });
    }

    /** One pending settle: the owner ref (for the upsert) and the latest-value reference rapid settles overwrite. */
    private static final class Settle {
        private final PlayerRef owner;
        private final AtomicReference<Money> value = new AtomicReference<>();

        private Settle(PlayerRef owner) {
            this.owner = owner;
        }
    }
}
