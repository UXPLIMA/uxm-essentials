package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.application.port.ItemPurse;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link ItemPurse} over a player's live inventory, changed on the player's own thread (Folia allows nothing
 * else) while the caller off the tick waits with a bound. The wait and the work race through {@link EntityHandoff}:
 * either the change never runs and the caller hears "not reachable", or it runs and the caller gets its answer, so a
 * delivery is never handed over twice by a late task.
 */
@NullMarked
public final class BukkitItemPurse implements ItemPurse {

    private static final Duration WAIT = Duration.ofSeconds(5);

    private final Scheduler scheduler;
    private final BukkitInventoryCalculations calculations;
    private final Logger log;
    private final EntityHandoff.Outcomes outcomes;

    public BukkitItemPurse(Scheduler scheduler, BukkitInventoryCalculations calculations, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.calculations = Objects.requireNonNull(calculations, "calculations");
        this.log = Objects.requireNonNull(log, "log");
        this.outcomes = new EntityHandoff.Outcomes() {
            @Override
            public void timedOut() {
                log.warn("event=swept_items_wait_timed_out after_ms={}", WAIT.toMillis());
            }

            @Override
            public void failed(Throwable failure) {
                log.error("event=swept_items_inventory_failed", failure);
            }
        };
    }

    @Override
    public Optional<Change> give(PlayerRef owner, Money amount) {
        return onPlayer(owner, player -> {
            BigDecimal before = calculations.getBalance(player, amount.currency());
            BigDecimal fit = calculations.creditFitting(player, amount);
            return new Change(before, before.add(fit));
        });
    }

    @Override
    public Optional<Change> setTo(PlayerRef owner, Money target) {
        return onPlayer(owner, player -> {
                    BigDecimal before = calculations.getBalance(player, target.currency());
                    BigDecimal diff = target.amount().subtract(before);
                    if (diff.signum() > 0) {
                        BigDecimal fit = calculations.creditFitting(player, Money.of(target.currency(), diff));
                        return Optional.of(new Change(before, before.add(fit)));
                    }
                    if (diff.signum() < 0) {
                        boolean taken = calculations
                                .debit(player, Money.of(target.currency(), diff.negate()))
                                .isOk();
                        if (!taken) {
                            // The items moved between the count and the take. Reporting the reset as done would
                            // leave the player above the figure for good; not done keeps it for the next pass.
                            log.warn(
                                    "event=swept_items_reset_take_failed player={} wanted={}",
                                    owner.uuid(),
                                    diff.negate());
                            return Optional.<Change>empty();
                        }
                        return Optional.of(new Change(before, target.amount()));
                    }
                    return Optional.of(new Change(before, before));
                })
                .flatMap(change -> change);
    }

    /** Run {@code work} on the player's thread; empty when they are not online here or the thread did not take it. */
    private <T> Optional<T> onPlayer(PlayerRef owner, Function<Player, T> work) {
        if (scheduler.ownsEntity(owner)) {
            Player player = Bukkit.getPlayer(owner.uuid());
            return player == null || !player.isOnline() ? Optional.empty() : Optional.of(work.apply(player));
        }
        EntityHandoff<Optional<T>> handoff = new EntityHandoff<>();
        scheduler.onEntity(
                owner,
                () -> handoff.run(() -> {
                    Player player = Bukkit.getPlayer(owner.uuid());
                    return player == null || !player.isOnline() ? Optional.<T>empty() : Optional.of(work.apply(player));
                }),
                () -> handoff.retire(Optional.empty()));
        return handoff.await(WAIT, Optional.empty(), outcomes);
    }
}
