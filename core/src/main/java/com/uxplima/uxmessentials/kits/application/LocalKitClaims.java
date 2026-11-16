package com.uxplima.uxmessentials.kits.application;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerClaimLedger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The {@link KitClaimStore} a single server uses: each player's own stamps (PDC), plus the one-time claims the
 * database records for them, which are copied into the stamps when the player joins. Those rows come from an import
 * of another plugin's claims, written while nearly every player is offline and has no stamps to write to, or from a
 * network the server was part of before. A claim made on this server stays a stamp only.
 *
 * <p>The rows are read during the asynchronous pre-login ({@link #load}) and stamped as the player joins
 * ({@link #joined}), so no check on the tick waits on the database and none runs before the stamps are in place. A
 * reset clears the stamps and the claims still waiting to be stamped at once, and deletes the rows off the tick. Until
 * that delete has landed the kit is held as being released, and neither a read nor a join brings it back: a read that
 * overlapped a reset is done again, and a kit still being released is skipped. A delete that fails keeps the kit held
 * here until the server stops, and says so, since the row would bring the claim back on a later start.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The claims waiting for a join and the kits being released are held in
 * {@link ConcurrentHashMap}s keyed by player, written by the pre-login thread, the player's own thread and the async
 * lane. A counter of resets lets a read tell whether a reset happened while it ran. The stamps are touched only on the
 * player's thread; database work runs on the async lane.
 */
public final class LocalKitClaims implements KitClaimStore {

    /** How many times a read overlapped by resets is done again before it settles for skipping the released kits. */
    private static final int READ_ATTEMPTS = 3;

    private final KitClaimStore stamps;
    private final PlayerClaimLedger ledger;
    private final Scheduler scheduler;
    private final Logger log;
    private final ConcurrentHashMap<UUID, Set<KitId>> waiting = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<KitId>> releasing = new ConcurrentHashMap<>();
    private final Set<UUID> releasingAll = ConcurrentHashMap.newKeySet();
    private final AtomicLong resets = new AtomicLong();

    public LocalKitClaims(KitClaimStore stamps, PlayerClaimLedger ledger, Scheduler scheduler, Logger log) {
        this.stamps = Objects.requireNonNull(stamps, "stamps");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean hasClaimed(PlayerRef who, KitId kit) {
        return stamps.hasClaimed(who, kit);
    }

    @Override
    public void markClaimed(PlayerRef who, KitId kit) {
        stamps.markClaimed(who, kit);
    }

    @Override
    public void reset(PlayerRef who, KitId kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        releasing
                .computeIfAbsent(who.uuid(), id -> ConcurrentHashMap.newKeySet())
                .add(kit);
        resets.incrementAndGet();
        stamps.reset(who, kit);
        drop(waiting, who.uuid(), kit);
        release(who, () -> ledger.releaseKit(who.uuid(), kit.value()), () -> drop(releasing, who.uuid(), kit));
    }

    @Override
    public void resetAll(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        releasingAll.add(who.uuid());
        resets.incrementAndGet();
        stamps.resetAll(who);
        waiting.remove(who.uuid());
        release(who, () -> ledger.releaseAllKits(who.uuid()), () -> releasingAll.remove(who.uuid()));
    }

    /**
     * Read the claims the database records for {@code player}, to stamp them when they join. Does I/O: called off the
     * tick, during the pre-login. A failed read leaves the player with the stamps they have and is logged.
     */
    public void load(UUID player) {
        Objects.requireNonNull(player, "player");
        for (int attempt = 0; attempt < READ_ATTEMPTS; attempt++) {
            long before = resets.get();
            Set<String> recorded;
            try {
                recorded = ledger.kits(player);
            } catch (RuntimeException unreachable) {
                log.warn(
                        "event=kit_claims_read_failed player={} reason={}; recorded one-time kit claims wait for the"
                                + " next login",
                        player,
                        String.valueOf(unreachable.getMessage()));
                return;
            }
            hold(player, recorded);
            if (resets.get() == before) {
                return;
            }
        }
    }

    private void hold(UUID player, Set<String> recorded) {
        Set<KitId> kits = ConcurrentHashMap.newKeySet();
        for (String id : recorded) {
            try {
                kits.add(KitId.of(id));
            } catch (IllegalArgumentException unusable) {
                log.debug("event=kit_claim_unusable player={} kit={}", player, id);
            }
        }
        kits.removeIf(kit -> beingReleased(player, kit));
        if (kits.isEmpty()) {
            waiting.remove(player);
        } else {
            waiting.put(player, kits);
        }
    }

    /** Stamp the claims {@link #load} read for {@code who}. Runs on the thread that owns {@code who}. */
    public void joined(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Set<KitId> kits = waiting.remove(who.uuid());
        if (kits == null) {
            return;
        }
        for (KitId kit : new HashSet<>(kits)) {
            if (!beingReleased(who.uuid(), kit)) {
                stamps.markClaimed(who, kit);
            }
        }
    }

    /** Forget what {@link #load} read for a player whose login never reached the world. */
    public void forget(UUID player) {
        waiting.remove(Objects.requireNonNull(player, "player"));
    }

    /** Read and stamp the recorded claims of {@code who}, who is already online: off the tick, then on their thread. */
    public void adopt(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        scheduler.async(() -> {
            load(who.uuid());
            scheduler.onEntity(who, () -> joined(who), () -> forget(who.uuid()));
        });
    }

    /**
     * Stamp {@code kit} for {@code who} now if they are online, for a claim just recorded in the database. A player who
     * is offline is stamped from the database on their next join instead.
     */
    public void stamp(PlayerRef who, KitId kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        scheduler.onEntity(who, () -> stamps.markClaimed(who, kit));
    }

    private boolean beingReleased(UUID player, KitId kit) {
        return releasingAll.contains(player)
                || releasing.getOrDefault(player, Set.of()).contains(kit);
    }

    private static void drop(ConcurrentHashMap<UUID, Set<KitId>> held, UUID player, KitId kit) {
        held.computeIfPresent(player, (id, kits) -> {
            kits.remove(kit);
            return kits.isEmpty() ? null : kits;
        });
    }

    /** Delete rows off the tick; {@code released} lets go of the hold only once the delete has landed. */
    private void release(PlayerRef who, Runnable delete, Runnable released) {
        scheduler.async(() -> {
            try {
                delete.run();
                released.run();
            } catch (RuntimeException failure) {
                log.warn(
                        "event=kit_claim_reset_failed player={} reason={}; the kit stays reset until the server stops,"
                                + " then the database brings the claim back: run /kit reset again",
                        who.name(),
                        String.valueOf(failure.getMessage()));
            }
        });
    }
}
