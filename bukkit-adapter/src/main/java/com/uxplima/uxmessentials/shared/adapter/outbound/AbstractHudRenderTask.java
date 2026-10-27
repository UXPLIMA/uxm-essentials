package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The shared scaffolding for the per-player HUD render loops (the scoreboard and tablist timers): a
 * self-rescheduling task on the {@link Scheduler} port. Each tick scans the live online set off the tick thread and, for each player, hops to that player's
 * region/entity thread before touching the live {@code Player}; no Bukkit entity is touched on the async loop
 * itself. A subclass supplies only the per-player render call and the log message for a failed tick.
 *
 * <p>The interval is read fresh each reschedule, so a {@code /uxmess reload} that swaps a new cadence in changes
 * the refresh rate on the next tick without re-arming the task. The task observes the module's {@code running}
 * flag and exits cleanly on disable.
 *
 * <p>The animation clock is global, not per-player: this loop calls {@link AnimationRegistry#advance()} exactly
 * once per tick on the loop thread (stepping every named animation to the tick's frame) before the per-player
 * fan-out. Each player then renders against the same captured frame, so an animation advances at most once per
 * tick no matter how many viewers are online and never flickers between viewers within a tick.
 *
 * <p>The roster is spread over the interval. The loop runs in slices of about {@link #SLICE}, and each player has
 * a fixed slice taken from their id, so every player still renders once per interval but never all on one tick: a
 * whole roster rendered at once cost a tick close to a millisecond per player. The animation clock steps on the
 * first slice of each interval.
 */
@NullMarked
public abstract class AbstractHudRenderTask {

    private final Scheduler scheduler;
    private final AnimationRegistry animations;
    private final Logger log;
    private final Supplier<Duration> interval;
    private final BooleanSupplier running;
    private final Set<UUID> pendingPlayers = ConcurrentHashMap.newKeySet();

    /** The slice of the interval the next loop tick serves. Only the loop touches it, one tick at a time. */
    private int slice;

    /** About one server tick: the finest step worth spreading the roster over. */
    static final Duration SLICE = Duration.ofMillis(50);

    protected AbstractHudRenderTask(
            Scheduler scheduler,
            AnimationRegistry animations,
            Logger log,
            Supplier<Duration> interval,
            BooleanSupplier running) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.log = Objects.requireNonNull(log, "log");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.running = Objects.requireNonNull(running, "running");
    }

    /** Arm the first render tick; subsequent ticks reschedule themselves until the module stops. */
    public final void start() {
        scheduleNext();
    }

    /**
     * Bring one roster-wide render pass forward, used after a successful live reload. Roster enumeration happens on
     * the global region and each render is still de-duplicated and hopped to the player's entity thread.
     */
    public final void refreshNow() {
        if (!running.getAsBoolean()) {
            return;
        }
        scheduler.onGlobal(() -> {
            if (!running.getAsBoolean()) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                enqueue(player);
            }
        });
    }

    private void scheduleNext() {
        if (!running.getAsBoolean()) {
            return;
        }
        Duration every = Objects.requireNonNull(interval.get(), "interval");
        scheduler.asyncAfter(every.dividedBy(slicesIn(every)), this::tick);
    }

    private void tick() {
        if (!running.getAsBoolean()) {
            return;
        }
        try {
            int slices = slicesIn(Objects.requireNonNull(interval.get(), "interval"));
            int current = slice % slices;
            slice = (current + 1) % slices;
            if (current == 0) {
                // Advance the global animation clock once per interval, on this loop thread, before fanning out.
                // Every render in this interval then reads the same frame.
                animations.advance();
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (Math.floorMod(player.getUniqueId().hashCode(), slices) == current) {
                    enqueue(player);
                }
            }
        } catch (RuntimeException failure) {
            // A throwing tick must not skip the reschedule below, or the HUD would freeze until a reload.
            log.error(tickFailureMessage(), failure);
        }
        scheduleNext();
    }

    /** How many slices of about {@link #SLICE} the interval holds, at least one. */
    private static int slicesIn(Duration every) {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, every.toMillis() / SLICE.toMillis()));
    }

    private void enqueue(Player player) {
        PlayerRef ref = BukkitRefs.toRef(player);
        if (pendingPlayers.add(ref.uuid())) {
            // A render the server drops on a retired entity must still clear the mark, or the player is never
            // queued again and their HUD stops on the frame it showed.
            scheduler.onEntity(ref, () -> renderPending(ref), () -> pendingPlayers.remove(ref.uuid()));
        }
    }

    private void renderPending(PlayerRef ref) {
        try {
            Player live = Bukkit.getPlayer(ref.uuid());
            if (live != null && live.isOnline() && running.getAsBoolean()) {
                renderFor(live);
            }
        } catch (RuntimeException failure) {
            log.error(tickFailureMessage(), failure);
        } finally {
            pendingPlayers.remove(ref.uuid());
        }
    }

    /** Render the HUD for one online player, already hopped onto that player's entity thread. */
    protected abstract void renderFor(Player player);

    /** The log message when a render tick throws; the loop still reschedules, so the HUD is never left frozen. */
    protected abstract String tickFailureMessage();
}
