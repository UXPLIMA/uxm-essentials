package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Reads the server's own playtime statistic, the one vanilla keeps in each player's stats file, for
 * {@code /playtime import}. An online player's statistic is read on their region thread; an offline player's comes
 * from their stats file on the async lane. Either way the answer is handed on on the async lane, where the import
 * writes the database. A player the server holds no statistics for reads as zero.
 */
@NullMarked
public final class VanillaPlaytime {

    /** The statistic counts ticks despite its name: one tick is 50 ms. */
    private static final long MILLIS_PER_TICK = 50L;

    private final Scheduler scheduler;

    public VanillaPlaytime(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Read {@code who}'s vanilla playtime and pass it to {@code then} on the async lane. */
    public void read(PlayerRef who, Consumer<Duration> then) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(then, "then");
        Runnable offline = () -> scheduler.async(() -> then.accept(of(Bukkit.getOfflinePlayer(who.uuid()))));
        if (Bukkit.getPlayer(who.uuid()) == null) {
            offline.run();
            return;
        }
        scheduler.onEntity(
                who,
                () -> {
                    Player player = Bukkit.getPlayer(who.uuid());
                    Duration played = player == null ? Duration.ZERO : of(player);
                    scheduler.async(() -> then.accept(played));
                },
                offline);
    }

    /** {@code account}'s vanilla playtime. Call it on the player's region thread when they are online. */
    public static Duration of(OfflinePlayer account) {
        Objects.requireNonNull(account, "account");
        try {
            return Duration.ofMillis(account.getStatistic(Statistic.PLAY_ONE_MINUTE) * MILLIS_PER_TICK);
        } catch (IllegalArgumentException | UnsupportedOperationException unavailable) {
            // A profile the server holds no statistics file for has played nothing the server knows of.
            return Duration.ZERO;
        }
    }
}
