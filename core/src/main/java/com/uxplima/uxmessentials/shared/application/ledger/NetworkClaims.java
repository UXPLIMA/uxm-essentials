package com.uxplima.uxmessentials.shared.application.ledger;

import java.time.Clock;
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
 * <p>A player whose claims have not been read yet is treated as having claimed everything and as waiting on every
 * cooldown: {@link #ready} is false, {@link #hasKit} is true and {@link #cooldownEnd} is empty. A slow database then
 * delays a grant instead of handing one out twice.
 *
 * <p>Memory alone cannot stop two servers from granting the same one-time kit: a player switching servers is read by
 * the new server before the old one has finished writing. {@link #acquireKit} therefore asks the ledger's unique key
 * before the kit is handed over, and only the server whose insert landed grants it.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. Entries live in a {@link ConcurrentHashMap}; each entry's sets are
 * concurrent, and the hand-over between the login read and the join carry-over is one short critical section on the
 * entry. No ledger call is made inside it.
 */
public final class NetworkClaims {

    private final PlayerClaimLedger ledger;
    private final Scheduler scheduler;
    private final Clock clock;
    private final Logger log;
    private final ConcurrentHashMap<UUID, Entry> entries = new ConcurrentHashMap<>();

    public NetworkClaims(PlayerClaimLedger ledger, Scheduler scheduler, Clock clock, Logger log) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Start reading {@code player}'s claims on the async lane, unless they are already held or being read. The future
     * completes once they are in memory, or exceptionally when the read failed (the player then stays locked until
     * the next attempt).
     */
    public CompletableFuture<Void> load(UUID player) {
        Objects.requireNonNull(player, "player");
        Entry fresh = new Entry();
        Entry existing = entries.putIfAbsent(player, fresh);
        if (existing != null) {
            return existing.loaded;
        }
        scheduler.async(() -> read(player, fresh));
        return fresh.loaded;
    }

    /** Mark {@code player} as in the world, reading their claims first if nothing holds them (a quick relog). */
    public void joined(UUID player) {
        var unused = load(player);
        Entry entry = entries.get(player);
        if (entry != null) {
            entry.joined = true;
        }
    }

    /** Let go of {@code player}'s claims; they are read again on the next login. */
    public void drop(UUID player) {
        entries.remove(Objects.requireNonNull(player, "player"));
    }

    /**
     * Let go of {@code player}'s claims only if they never reached the world: a login refused after it was read. A
     * player who did join is let go by {@link #drop} when they quit, so a late close of an older connection cannot
     * throw away the claims of the session that replaced it.
     */
    public void dropUnjoined(UUID player) {
        entries.computeIfPresent(Objects.requireNonNull(player, "player"), (id, entry) -> entry.joined ? entry : null);
    }

    /** Whether {@code player}'s claims are in memory, so a check answers from fact rather than failing safe. */
    public boolean ready(UUID player) {
        return readyEntry(player) != null;
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
        scheduler.async(() -> applyCarry(writes, carried));
    }

    /** Whether {@code player} has claimed the one-time kit {@code kit}; true while their claims are not read yet. */
    public boolean hasKit(UUID player, String kit) {
        Objects.requireNonNull(kit, "kit");
        Entry entry = readyEntry(player);
        return entry == null || entry.kits.contains(kit);
    }

    /**
     * Take {@code who}'s claim on the one-time kit {@code kit} in the ledger before the kit is granted. The future
     * completes on the thread that owns {@code who}: true when this server's insert landed, false when the claim was
     * already taken (here or on another server). It completes exceptionally when the ledger failed, or with a
     * {@link CancellationException} when the player left before the answer came back, in which case a claim this
     * server won is given back.
     */
    public CompletableFuture<Boolean> acquireKit(PlayerRef who, String kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        Entry entry = readyEntry(who.uuid());
        if (entry == null) {
            answer.complete(false);
            return answer;
        }
        long now = clock.millis();
        scheduler.async(() -> {
            boolean inserted;
            try {
                inserted = ledger.claimKit(who.uuid(), kit, now);
            } catch (RuntimeException failure) {
                log.error("could not record the one-time kit " + kit + " for " + who.name(), failure);
                scheduler.onEntity(
                        who, () -> answer.completeExceptionally(failure), () -> answer.completeExceptionally(failure));
                return;
            }
            // Claimed either way now: by this insert, or by the one that beat it.
            entry.kits.add(kit);
            scheduler.onEntity(who, () -> answer.complete(inserted), () -> {
                if (inserted) {
                    releaseKit(who.uuid(), kit);
                }
                answer.completeExceptionally(new CancellationException("the player left before the kit was granted"));
            });
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
        write("record the one-time kit " + kit, () -> ledger.claimKit(player, kit, now));
    }

    /** Forget {@code player}'s claim on {@code kit}, in memory and in the ledger. */
    public void releaseKit(UUID player, String kit) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kit, "kit");
        Entry entry = entries.get(player);
        if (entry != null) {
            entry.kits.remove(kit);
        }
        write("reset the one-time kit " + kit, () -> ledger.releaseKit(player, kit));
    }

    /** Forget every one-time kit claim {@code player} holds, in memory and in the ledger. */
    public void releaseAllKits(UUID player) {
        Objects.requireNonNull(player, "player");
        Entry entry = entries.get(player);
        if (entry != null) {
            entry.kits.clear();
        }
        write("reset the one-time kits", () -> ledger.releaseAllKits(player));
    }

    /**
     * When {@code player}'s cooldown under {@code scope} ends, in epoch milliseconds, {@code 0} when there is none;
     * empty while their claims are not read yet.
     */
    public OptionalLong cooldownEnd(UUID player, String scope) {
        Objects.requireNonNull(scope, "scope");
        Entry entry = readyEntry(player);
        if (entry == null) {
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
        write("save the cooldown " + scope, () -> ledger.saveCooldown(player, scope, readyAt));
    }

    private void read(UUID player, Entry entry) {
        Claims claims;
        try {
            claims = ledger.load(player, clock.millis());
        } catch (RuntimeException failure) {
            entries.remove(player, entry);
            log.error(
                    "could not read the kit claims and cooldowns of " + player + "; they stay locked until read",
                    failure);
            entry.loaded.completeExceptionally(failure);
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

    /** Run the carry's writes on the calling (async) thread; {@code carried} only after every one of them landed. */
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

    private void write(String what, Runnable io) {
        scheduler.async(() -> {
            try {
                io.run();
            } catch (RuntimeException failure) {
                log.error("could not " + what + " in the database", failure);
            }
        });
    }

    private @Nullable Entry readyEntry(UUID player) {
        Entry entry = entries.get(Objects.requireNonNull(player, "player"));
        return entry != null && entry.ready ? entry : null;
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
    }
}
