package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.application.RentConfig;
import com.uxplima.uxmessentials.playerwarps.application.RentOutcome;
import com.uxplima.uxmessentials.playerwarps.application.RentReminders;
import com.uxplima.uxmessentials.playerwarps.application.SettleRent;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.RentReminderCandidate;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The self-rescheduling rent sweep (the {@code SalaryTask}/{@code BankInterestTask} pattern): on a fixed interval,
 * off the tick thread, it charges the warps whose paid term has lapsed, retries or archives the suspended ones, and
 * mails any owners a heads-up whose term is approaching. Every pass is bounded: each query is capped at
 * {@link #BATCH_LIMIT} rows and indexed on {@code rent_paid_until}, never a full-table scan, and each warp is settled
 * in isolation, so one warp's fault (a transient DB error, say) is logged and the sweep carries on.
 *
 * <p>The scheduling, stop flag and enable gate live in {@link AbstractPeriodicSweep}; this sweep supplies only the
 * per-pass work.
 */
@NullMarked
public final class RentSweep extends AbstractPeriodicSweep {

    private final PlayerWarpRepository repository;
    private final SettleRent settle;
    private final RentReminders reminders;
    private final RentConfig config;

    public RentSweep(
            PlayerWarpRepository repository,
            SettleRent settle,
            RentReminders reminders,
            RentConfig config,
            Scheduler scheduler,
            Duration interval,
            Clock clock,
            Logger log) {
        super(scheduler, interval, clock, log);
        this.repository = Objects.requireNonNull(repository, "repository");
        this.settle = Objects.requireNonNull(settle, "settle");
        this.reminders = Objects.requireNonNull(reminders, "reminders");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    protected boolean enabled() {
        return config.enabled();
    }

    /** One bounded pass: charge the due warps, retry/archive the suspended ones, then send any due reminders. */
    @Override
    public void sweepOnce() {
        Instant now = clock.instant();
        int charged = settleBatch(repository.dueForRent(now, BATCH_LIMIT));
        int retried = settleBatch(repository.suspendedForRent(BATCH_LIMIT));
        int reminded = remindBatch(now);
        if (charged + retried + reminded > 0) {
            log.info("event=playerwarp_rent_sweep charged={} retried={} reminded={}", charged, retried, reminded);
        }
    }

    private int settleBatch(List<PlayerWarp> warps) {
        int changed = 0;
        for (PlayerWarp warp : warps) {
            try {
                if (settle.settle(warp) != RentOutcome.UNCHANGED) {
                    changed++;
                }
            } catch (RuntimeException failure) {
                log.error("event=playerwarp_rent_settle_failed warp=" + warpId(warp), failure);
            }
        }
        return changed;
    }

    private int remindBatch(Instant now) {
        if (config.reminderWindows().isEmpty()) {
            return 0;
        }
        Instant horizon = now.plus(config.widestReminderWindow());
        int sent = 0;
        List<RentReminderCandidate> candidates =
                repository.remindableForRent(now, horizon, config.maxReminderStage(), BATCH_LIMIT);
        for (RentReminderCandidate candidate : candidates) {
            try {
                if (reminders.remind(candidate)) {
                    sent++;
                }
            } catch (RuntimeException failure) {
                log.error(
                        "event=playerwarp_rent_reminder_failed warp="
                                + candidate.id().value(),
                        failure);
            }
        }
        return sent;
    }
}
