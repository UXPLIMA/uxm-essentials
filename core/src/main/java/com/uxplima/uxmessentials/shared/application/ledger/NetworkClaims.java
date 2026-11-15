package com.uxplima.uxmessentials.shared.application.ledger;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerClaimLedger;
import com.uxplima.uxmessentials.shared.application.port.PlayerClaimLedger.Claims;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * Each online player's one-time kit claims and cooldowns, read from the shared {@link PlayerClaimLedger} when they log
 * in and held in memory, so a check on the tick never waits on the database. Every change is applied to memory at
 * once and written through on the async lane.
 *
 * <p>Three states, by what is known about a player:
 *
 * <ul>
 *   <li><b>Read.</b> Checks answer from memory.
 *   <li><b>Being read.</b> The player is logging in, or a failed read is being retried with a growing pause. Checks
 *       fail safe: {@link #ready} is false, {@link #hasKit} is true and {@link #cooldownEnd} is empty, so a slow
 *       database delays a grant instead of handing one out twice.
 *   <li><b>Unknown.</b> Nothing is held: the console, a command block, another plugin's system actor, or a player
 *       who is not online here. Checks pass as they did in PDC, and a one-time claim still goes to the ledger's
 *       unique key.
 * </ul>
 *
 * <p>Memory alone cannot stop two servers from granting the same one-time kit: a player switching servers is read by
 * the new server before the old one has finished writing. {@link #acquireKit} therefore asks the ledger's unique key
 * before the kit is handed over, and only the server whose insert landed grants it. Cooldowns are last write wins, so
 * a repeatable kit can be claimed once more by a player who switches servers faster than the old server writes the
 * stamp; the window is the length of one database write.
 *
 * <p>A change made on another server (an admin's {@code /kit reset} there) reaches a player's memory here on their
 * next login. Until then it errs on the strict side: a reset kit stays claimed here, a claim made there is caught by
 * the unique key.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. Entries live in a {@link ConcurrentHashMap}; each entry's sets are
 * concurrent, and the hand-over between the login read and the join carry-over is one short critical section on the
 * entry. No ledger call is made inside it. Every ledger call for one player runs in order, one after another, on a
 * per-player chain, so a reset and a later claim, or two stamps of one cooldown, reach the database in the order they
 * were made.
 */
public final class NetworkClaims {

    private static final Duration FIRST_RETRY = Duration.ofSeconds(1);
    private static final Duration LONGEST_RETRY = Duration.ofSeconds(30);

    private final PlayerClaimLedger ledger;
    private final Scheduler scheduler;
    private final Clock clock;
    private final Logger log;
    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> chains = new ConcurrentHashMap<>();
    private final Executor lane = this::onLane;
    private volatile boolean closing;

    public NetworkClaims(PlayerClaimLedger ledger, Scheduler scheduler, Clock clock, Logger log) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Start reading {@code player}'s claims, unless they are already held or being read. The future completes once
     * they are in memory, and is cancelled when the player is let go first. A failed read is retried while the player
     * is held, so the future does not fail on a database hiccup.
     */
    public CompletableFuture<Void> load(UUID player) {
        return hold(player, false).loaded;
    }

    /** Mark {@code player} as in the world, reading their claims first if nothing holds them (a quick relog). */
    public void joined(UUID player) {
        hold(player, true);
    }

    /** Let go of {@code player}'s claims; they are read again on the next login. */
    public void drop(UUID player) {
        Entry entry = entries.remove(Objects.requireNonNull(player, "player"));
        if (entry != null) {
            entry.loaded.completeExceptionally(new CancellationException("the player left"));
        }
    }

    /**
     * Let go of {@code player}'s claims only if they never reached the world: a login refused after it was read. A
     * player who did join is let go by {@link #drop} when they quit, so a late close of an older connection cannot
     * throw away the claims of the session that replaced it.
     */
    public void dropUnjoined(UUID player) {
        Objects.requireNonNull(player, "player");
        AtomicReference<@Nullable Entry> gone = new AtomicReference<>();
        entries.computeIfPresent(player, (id, entry) -> {
            if (entry.joined) {
                return entry;
            }
            gone.set(entry);
            return null;
        });
        Entry removed = gone.get();
        if (removed != null) {
            removed.loaded.completeExceptionally(new CancellationException("the login was refused"));
        }
    }

    /**
     * Whether a check for {@code player} answers from fact. False only while their claims are being read; a player
     * nothing is held for (the console, an offline player) is not waited on.
     */
    public boolean ready(UUID player) {
        Entry entry = entries.get(Objects.requireNonNull(player, "player"));
        return entry == null || entry.ready;
    }

    /**
     * Fold what this server's PDC says into {@code player}'s claims, keeping the stricter value: a kit claimed in
     * either place stays claimed, and of two cooldowns the one that ends later wins. Anything the PDC adds is written
     * to the ledger, and {@code carried} runs once those writes are done. When the claims are still being read, the
     * merge waits for them, and the player stays not {@link #ready} until it has run.
     */
    public void carryOver(UUID player, LocalClaims local, Runnable carried) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(carried, "carried");
        Entry entry = entries.get(player);
        if (entry == null) {
            return;
        }
        List<Runnable> writes;
        synchronized (entry) {
            if (!entry.ready) {
                entry.pendingCarry = new Carry(local, carried);
                return;
            }
            writes = merge(player, entry, local);
        }
        enqueue(player, () -> applyCarry(writes, carried));
    }

    /**
     * Whether {@code player} has claimed the one-time kit {@code kit}: true while their claims are being read, false
     * for a player nothing is held for.
     */
    public boolean hasKit(UUID player, String kit) {
        Objects.requireNonNull(kit, "kit");
        Entry entry = entries.get(Objects.requireNonNull(player, "player"));
        if (entry == null) {
            return false;
        }
        return !entry.ready || entry.kits.contains(kit);
    }

    /**
     * Take {@code who}'s claim on the one-time kit {@code kit} in the ledger before the kit is granted, after their
     * read when one is running. The future completes on the thread that owns {@code who}: true when this server's
     * insert landed, false when the claim was already taken (here or on another server). It completes exceptionally
     * when the ledger failed, or with a {@link CancellationException} when the player left before the answer came
     * back, in which case a claim this server won is given back.
     */
    public CompletableFuture<Boolean> acquireKit(PlayerRef who, String kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        Entry entry = entries.get(who.uuid());
        CompletableFuture<Void> readFirst = entry == null ? CompletableFuture.completedFuture(null) : entry.loaded;
        var unused = readFirst.whenComplete((ignored, failure) -> {
            if (failure != null) {
                answer.completeExceptionally(failure);
                return;
            }
            enqueue(who.uuid(), () -> insert(who, kit, entry, answer));
        });
        return answer;
    }

    /** Record {@code player}'s claim on {@code kit} without asking who was first: a grant that skipped the claim. */
    public void markKit(UUID player, String kit) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kit, "kit");
        Entry entry = entries.get(player);
        if (entry != null && !entry.kits.add(kit)) {
            return;
        }
        long now = clock.millis();
        write(player, "record the one-time kit " + kit, () -> ledger.claimKit(player, kit, now));
    }

    /** Forget {@code player}'s claim on {@code kit}, in memory and in the ledger. */
    public void releaseKit(UUID player, String kit) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kit, "kit");
        Entry entry = entries.get(player);
        if (entry != null) {
            entry.kits.remove(kit);
        }
        write(player, "reset the one-time kit " + kit, () -> ledger.releaseKit(player, kit));
    }

    /** Forget every one-time kit claim {@code player} holds, in memory and in the ledger. */
    public void releaseAllKits(UUID player) {
        Objects.requireNonNull(player, "player");
        Entry entry = entries.get(player);
        if (entry != null) {
            entry.kits.clear();
        }
        write(player, "reset the one-time kits", () -> ledger.releaseAllKits(player));
    }

    /**
     * When {@code player}'s cooldown under {@code scope} ends, in epoch milliseconds, {@code 0} when there is none or
     * nothing is held for them; empty while their claims are being read.
     */
    public OptionalLong cooldownEnd(UUID player, String scope) {
        Objects.requireNonNull(scope, "scope");
        Entry entry = entries.get(Objects.requireNonNull(player, "player"));
        if (entry == null) {
            return OptionalLong.of(0L);
        }
        if (!entry.ready) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(entry.cooldowns.getOrDefault(scope, 0L));
    }

    /** Start {@code player}'s cooldown under {@code scope}, ending at {@code readyAt}; the last write wins. */
    public void stampCooldown(UUID player, String scope, long readyAt) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(scope, "scope");
        Entry entry = entries.get(player);
        if (entry != null) {
            entry.cooldowns.put(scope, readyAt);
        }
        write(player, "save the cooldown " + scope, () -> ledger.saveCooldown(player, scope, readyAt));
    }

    /**
     * Finish every write still waiting, for shutdown: from here on queued and new writes run on the calling thread,
     * because a stopping plugin may no longer hand work to the async lane. Waits at most {@code timeout} for writes
     * already running.
     */
    public void drain(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        closing = true;
        CompletableFuture<?>[] pending = chains.values().toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(pending).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException unfinished) {
            log.warn("event=network_claims_drain_incomplete pending={}", String.valueOf(pending.length));
        }
    }

    private Entry hold(UUID player, boolean joined) {
        Objects.requireNonNull(player, "player");
        AtomicBoolean fresh = new AtomicBoolean();
        // One compute, so a late dropUnjoined from an older connection cannot remove the entry between the lookup
        // and the joined mark.
        Entry entry = entries.compute(player, (id, existing) -> {
            Entry held = existing;
            if (held == null) {
                held = new Entry();
                fresh.set(true);
            }
            if (joined) {
                held.joined = true;
            }
            return held;
        });
        if (fresh.get()) {
            enqueue(player, () -> read(player, entry));
        }
        return entry;
    }

    private void read(UUID player, Entry entry) {
        if (entries.get(player) != entry) {
            return;
        }
        Claims claims;
        try {
            claims = ledger.load(player, clock.millis());
        } catch (RuntimeException failure) {
            retry(player, entry, failure);
            return;
        }
        List<Runnable> writes = List.of();
        @Nullable Carry pending;
        synchronized (entry) {
            entry.kits.addAll(claims.kits());
            entry.cooldowns.putAll(claims.cooldowns());
            pending = entry.pendingCarry;
            entry.pendingCarry = null;
            if (pending != null) {
                writes = merge(player, entry, pending.local());
            }
            entry.ready = true;
        }
        if (pending != null) {
            applyCarry(writes, pending.carried());
        }
        entry.loaded.complete(null);
    }

    /** Read again after a pause that doubles with each failure, for as long as the player is held. */
    private void retry(UUID player, Entry entry, RuntimeException failure) {
        entry.failures++;
        long seconds = Math.min(LONGEST_RETRY.toSeconds(), FIRST_RETRY.toSeconds() << Math.min(entry.failures - 1, 5));
        Duration pause = Duration.ofSeconds(seconds);
        if (entry.failures == 1) {
            log.error(
                    "could not read the kit claims and cooldowns of " + player + "; they stay locked and the read is"
                            + " tried again while they are online",
                    failure);
        } else {
            log.warn(
                    "event=network_claims_read_failed player={} attempt={} retry_in_s={} reason={}",
                    player,
                    String.valueOf(entry.failures),
                    String.valueOf(seconds),
                    failure.toString());
        }
        if (closing) {
            return;
        }
        scheduler.asyncAfter(pause, () -> {
            if (entries.get(player) == entry) {
                enqueue(player, () -> read(player, entry));
            }
        });
    }

    private void insert(PlayerRef who, String kit, @Nullable Entry entry, CompletableFuture<Boolean> answer) {
        long now = clock.millis();
        boolean inserted;
        try {
            inserted = ledger.claimKit(who.uuid(), kit, now);
        } catch (RuntimeException failure) {
            log.error("could not record the one-time kit " + kit + " for " + who.name(), failure);
            scheduler.onEntity(
                    who, () -> answer.completeExceptionally(failure), () -> answer.completeExceptionally(failure));
            return;
        }
        if (entry != null) {
            // Claimed either way now: by this insert, or by the one that beat it.
            entry.kits.add(kit);
        }
        scheduler.onEntity(who, () -> answer.complete(inserted), () -> {
            if (inserted) {
                releaseKit(who.uuid(), kit);
            }
            answer.completeExceptionally(new CancellationException("the player left before the kit was granted"));
        });
    }

    private List<Runnable> merge(UUID player, Entry entry, LocalClaims local) {
        long now = clock.millis();
        List<Runnable> writes = new ArrayList<>();
        for (String kit : local.kits()) {
            if (entry.kits.add(kit)) {
                writes.add(() -> ledger.claimKit(player, kit, now));
            }
        }
        for (Map.Entry<String, Long> cooldown : local.cooldowns().entrySet()) {
            String scope = cooldown.getKey();
            long end = cooldown.getValue();
            if (end > now && end > entry.cooldowns.getOrDefault(scope, 0L)) {
                entry.cooldowns.put(scope, end);
                writes.add(() -> ledger.saveCooldown(player, scope, end));
            }
        }
        return writes;
    }

    /** Run the carry's writes on the player's chain; {@code carried} only after every one of them landed. */
    private void applyCarry(List<Runnable> writes, Runnable carried) {
        try {
            writes.forEach(Runnable::run);
        } catch (RuntimeException failure) {
            log.error(
                    "could not carry kit claims and cooldowns into the database; trying again on the next join",
                    failure);
            return;
        }
        carried.run();
    }

    private void write(UUID player, String what, Runnable io) {
        enqueue(player, () -> {
            try {
                io.run();
            } catch (RuntimeException failure) {
                log.error("could not " + what + " in the database", failure);
            }
        });
    }

    /** Run {@code step} after every earlier step for {@code player}; a failing step never stalls the ones after it. */
    private void enqueue(UUID player, Runnable step) {
        Runnable guarded = () -> {
            try {
                step.run();
            } catch (RuntimeException failure) {
                log.error("a database step for " + player + " failed", failure);
            }
        };
        CompletableFuture<Void> done = new CompletableFuture<>();
        // The swap is atomic, so each step waits on exactly the one queued before it. The step itself runs outside
        // the map, because while stopping it runs on this thread and may queue another step.
        CompletableFuture<Void> previous = chains.put(player, done);
        CompletableFuture<Void> before = previous == null ? CompletableFuture.completedFuture(null) : previous;
        var unused = before.whenCompleteAsync(
                (ignored, failure) -> {
                    try {
                        guarded.run();
                    } finally {
                        chains.remove(player, done);
                        done.complete(null);
                    }
                },
                lane);
    }

    private void onLane(Runnable task) {
        if (closing) {
            task.run();
            return;
        }
        scheduler.async(task);
    }

    private record Carry(LocalClaims local, Runnable carried) {}

    private static final class Entry {
        private final CompletableFuture<Void> loaded = new CompletableFuture<>();
        private final Set<String> kits = ConcurrentHashMap.newKeySet();
        private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
        private volatile boolean ready;
        private volatile boolean joined;
        // Guarded by the entry's monitor: set by a join that arrived before the login read finished.
        private @Nullable Carry pendingCarry;
        // Touched only by this player's read steps, which run one at a time on the player's chain.
        private int failures;
    }
}
