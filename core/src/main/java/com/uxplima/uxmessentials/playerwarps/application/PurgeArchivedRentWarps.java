package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.RentState;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpDeleted;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * The last step of the rent lifecycle, and the only one that cannot be undone: a warp archived for unpaid rent that
 * nobody has restored or touched for {@link RentConfig#hardDeleteAfter} is deleted for good, freeing its name. Off
 * unless the operator sets a delay, so an archived warp stays recoverable by default.
 *
 * <p>Only warps {@link SettleRent} retired qualify: they carry a rent archive deadline, which a warp the owner
 * archived while paid up never has. A warp the owner archived while it was suspended for rent still carries one, so
 * it counts as retired for rent. The delay counts from both that deadline and the warp's last change, so a warp an
 * admin restored and that was archived again gets the full delay once more. An exempt warp is left alone, as the
 * rest of the rent sweep leaves it.
 *
 * <p>A warp can still hold earnings when it is archived (a bank in another currency than the rent, or less than one
 * term), so the bank is paid to the owner first and a warp whose payout fails is kept for the next pass. The delete
 * itself goes through {@link PlayerWarpRepository#deleteIfArchivedForRent}, which checks the same condition and an
 * empty bank inside the delete: the page read earlier can be stale by then (a restore, a payment, another backend
 * running the same sweep), and only a delete that happened is published and counted.
 *
 * <p>The delete publishes {@link PlayerWarpDeleted}, the same as the admin delete, so cross-server caches and the
 * side tables are cleaned up the same way. No veto is asked: the operator chose the delay, as an operator confirms
 * the admin delete.
 */
@NullMarked
public final class PurgeArchivedRentWarps {

    private final PlayerWarpRepository repository;
    private final PlayerWarpEconomy economy;
    private final DomainEventPublisher events;
    private final RentConfig config;
    private final Clock clock;
    private final Logger log;

    public PurgeArchivedRentWarps(
            PlayerWarpRepository repository,
            PlayerWarpEconomy economy,
            DomainEventPublisher events,
            RentConfig config,
            Clock clock,
            Logger log) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.events = Objects.requireNonNull(events, "events");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * One pass over up to {@code limit} due warps: each is purged in isolation, so one warp's fault is logged and the
     * pass carries on, and a failing page read is logged and ends only this pass. Returns how many were deleted.
     */
    public int purgeDue(int limit) {
        List<PlayerWarp> due;
        try {
            due = due(limit);
        } catch (RuntimeException failure) {
            log.error("event=playerwarp_rent_hard_delete_query_failed", failure);
            return 0;
        }
        List<String> deleted = new ArrayList<>();
        for (PlayerWarp warp : due) {
            try {
                if (purge(warp)) {
                    deleted.add(token(warp) + ":" + warp.name().value());
                }
            } catch (RuntimeException failure) {
                log.error("event=playerwarp_rent_hard_delete_failed warp=" + token(warp), failure);
            }
        }
        // A hard delete cannot be undone, so the operator always sees which warps went, by id and name, to find them
        // in a backup.
        if (!deleted.isEmpty()) {
            log.info(
                    "event=playerwarp_rent_hard_deleted count={} after_days={} warps={}",
                    deleted.size(),
                    config.hardDeleteAfter().toDays(),
                    String.join(",", deleted));
        }
        return deleted.size();
    }

    /** Up to {@code limit} warps whose hard delete is due now; empty when hard delete is off. */
    public List<PlayerWarp> due(int limit) {
        if (!config.hardDeletes()) {
            return List.of();
        }
        return repository.archivedForRent(cutoff(), limit);
    }

    /**
     * Delete {@code warp} for good when its hard delete is due, after paying its bank to the owner; true only when
     * this call deleted it.
     */
    public boolean purge(PlayerWarp warp) {
        Objects.requireNonNull(warp, "warp");
        Instant cutoff = cutoff();
        if (!config.hardDeletes() || config.isExempt(warp) || !qualifies(warp, cutoff)) {
            return false;
        }
        Optional<PlayerWarpId> id = warp.id();
        if (id.isEmpty()) {
            return false;
        }
        Result<Unit, ChargeError> paid = economy.withdraw(id.get(), warp.owner());
        if (paid.isErr()) {
            return false;
        }
        if (!repository.deleteIfArchivedForRent(id.get(), cutoff)) {
            return false;
        }
        events.publish(new PlayerWarpDeleted(warp.owner(), warp.name()));
        return true;
    }

    private static String token(PlayerWarp warp) {
        return warp.id().map(id -> Long.toString(id.value())).orElse("?");
    }

    private Instant cutoff() {
        return clock.instant().minus(config.hardDeleteAfter());
    }

    private static boolean qualifies(PlayerWarp warp, Instant cutoff) {
        if (warp.status() != WarpStatus.ARCHIVED || warp.updatedAt().isAfter(cutoff)) {
            return false;
        }
        return warp.rent()
                .flatMap(RentState::archiveAfter)
                .map(deadline -> !deadline.isAfter(cutoff))
                .orElse(false);
    }
}
