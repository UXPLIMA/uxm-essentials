package com.uxplima.uxmessentials.presence.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.presence.application.port.NickDisplay;
import com.uxplima.uxmessentials.presence.application.port.NickRecords;
import com.uxplima.uxmessentials.presence.domain.NickState;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.settings.HeldPlayerState;
import com.uxplima.uxmessentials.shared.application.settings.HeldSettings;

/**
 * The players' nicks, kept in the database so a nick survives a relog and follows the player to every backend. The
 * nick of a player online here is held in memory, read during their login and shown when they join; a change is shown
 * at once and written back off the tick, then announced, so a backend the player is on reads and shows it too. A nick
 * set for a player who is not here is written straight to the database and announced the same way.
 */
public final class PlayerNicks {

    private final HeldPlayerState<NickState> held;
    private final NickRecords records;
    private final NickDisplay display;
    private final Scheduler scheduler;
    private final Logger log;
    private final Clock clock;
    private final Consumer<UUID> announce;

    /** @param announce tells the other backends, off the tick, that a player's nick was written */
    public PlayerNicks(
            NickRecords records,
            NickDisplay display,
            Scheduler scheduler,
            Logger log,
            Clock clock,
            Consumer<UUID> announce) {
        this.records = Objects.requireNonNull(records, "records");
        this.display = Objects.requireNonNull(display, "display");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.announce = Objects.requireNonNull(announce, "announce");
        this.held = new HeldPlayerState<>(
                "nick",
                new HeldPlayerState.Storage<>() {
                    @Override
                    public NickState read(UUID player) {
                        return records.read(player);
                    }

                    @Override
                    public void write(UUID player, NickState before, NickState after) {
                        records.write(player, after);
                    }
                },
                NickState.NONE,
                scheduler,
                log,
                announce,
                // Shows a nick another backend set while the player is here; at login the join shows it.
                player -> display.show(player, held().get(player).value()));
    }

    private HeldPlayerState<NickState> held() {
        return held;
    }

    /** The settings sessions and the bus read and let go of. */
    public HeldSettings settings() {
        return held;
    }

    /** The nick of {@code player}, held while they are here. Never reads the database. */
    public Optional<String> nickOf(UUID player) {
        return held.get(Objects.requireNonNull(player, "player")).value();
    }

    /**
     * What the database was last known to hold for {@code player}: empty when their login read failed, so nothing
     * is known about it.
     */
    public Optional<NickState> stored(UUID player) {
        return held.stored(Objects.requireNonNull(player, "player"));
    }

    /** {@code player} joined: show their nick. */
    public void joined(UUID player) {
        display.show(Objects.requireNonNull(player, "player"), nickOf(player));
    }

    /** Give {@code player} the nick {@code nick}, or none when it is empty. */
    public void set(UUID player, String nick) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(nick, "nick");
        NickState next = new NickState(nick, clock.millis());
        if (held.holds(player)) {
            held.set(player, next);
            display.show(player, next.value());
            return;
        }
        scheduler.async(() -> {
            try {
                records.write(player, next);
            } catch (RuntimeException failure) {
                log.warn("event=nick_write_failed player={} reason={}", player, String.valueOf(failure.getMessage()));
                return;
            }
            announce.accept(player);
        });
    }
}
