package com.uxplima.uxmessentials.presence.application;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.presence.application.port.NickDisplay;
import com.uxplima.uxmessentials.presence.application.port.NickRecords;
import com.uxplima.uxmessentials.presence.application.port.NickWrite;
import com.uxplima.uxmessentials.presence.domain.NickRules;
import com.uxplima.uxmessentials.presence.domain.NickState;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.settings.HeldPlayerState;
import com.uxplima.uxmessentials.shared.application.settings.HeldSettings;
import com.uxplima.uxmessentials.shared.domain.PlayerName;

/**
 * The players' nicks, kept in the database so a nick survives a relog and follows the player to every backend. The
 * nick of a player online here is held in memory, read during their login and shown when they join.
 *
 * <p>A nick is claimed rather than queued: the check that nobody else holds it and the write run in one task off the
 * tick, and the store refuses a nick another player holds and a write over a row that changed since it was read. Only
 * a write that landed is shown and announced, so the backend the player is on reads and shows it too; a refused one
 * reads the row again.
 */
public final class PlayerNicks {

    /** More accounts under one name than a server ever sees; one other is enough to refuse. */
    private static final int ACCOUNTS_READ = 8;
    /** How many times clearing an impersonating nick reads again after another write overtook it. */
    private static final int RELEASE_ATTEMPTS = 3;

    private final HeldPlayerState<NickState> held;
    private final NickDisplay display;
    private final NickRecords records;
    private final PlayerNameRepository names;
    private final Logger log;
    private final Consumer<UUID> announce;

    /**
     * @param names the accounts every backend has seen, by the name each last joined with
     * @param announce tells the other backends, off the tick, that a player's nick was written
     */
    public PlayerNicks(
            NickRecords records,
            PlayerNameRepository names,
            NickDisplay display,
            Scheduler scheduler,
            Logger log,
            Consumer<UUID> announce) {
        this.records = Objects.requireNonNull(records, "records");
        this.names = Objects.requireNonNull(names, "names");
        this.log = Objects.requireNonNull(log, "log");
        this.announce = Objects.requireNonNull(announce, "announce");
        this.display = Objects.requireNonNull(display, "display");
        this.held = new HeldPlayerState<>(
                "nick",
                new HeldPlayerState.Storage<>() {
                    @Override
                    public NickState read(UUID player) {
                        return records.read(player);
                    }

                    @Override
                    public void write(UUID player, NickState before, NickState after) {
                        // Nicks are claimed through the store, which refuses a taken or out of date one; never queued.
                        throw new UnsupportedOperationException("a nick is claimed, not written through");
                    }
                },
                NickState.NONE,
                scheduler,
                log,
                player -> {},
                // A read of the row shows what it holds: after a write here, and after one on another backend.
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
     * What the database was last known to hold for {@code player}: empty when their login read failed, so nothing is
     * known about it.
     */
    public Optional<NickState> stored(UUID player) {
        return held.stored(Objects.requireNonNull(player, "player"));
    }

    /** {@code player} joined: show their nick. */
    public void joined(UUID player) {
        display.show(Objects.requireNonNull(player, "player"), nickOf(player));
    }

    /**
     * Give {@code player} the nick {@code nick}, already found to be of an allowed shape, unless another account holds
     * it as its name or its nick. Reads and writes the database: call it off the tick.
     */
    public NickWrite claim(UUID player, String nick) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(nick, "nick");
        if (namedByAnother(nick, player)) {
            return new NickWrite.Taken();
        }
        return write(player, records.read(player).version(), nick);
    }

    /** Take {@code player}'s nick away. Reads and writes the database: call it off the tick. */
    public NickWrite clear(UUID player) {
        Objects.requireNonNull(player, "player");
        NickState current = records.read(player);
        if (current.value().isEmpty()) {
            return new NickWrite.Written(current);
        }
        return write(player, current.version(), "");
    }

    /**
     * Take over {@code nick}, kept for {@code player} by an earlier version that only checked its shape, when the
     * database holds no nick for them and nobody else holds this one. Whether the old value is settled: written,
     * refused, or overtaken by a nick the database now holds. Reads and writes the database: call it off the tick.
     */
    public boolean adoptLegacy(UUID player, String accountName, String nick) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(accountName, "accountName");
        String trimmed = Objects.requireNonNull(nick, "nick").strip();
        if (!NickRules.allowed(trimmed, accountName)) {
            log.info("event=legacy_nick_dropped player={} reason=shape", player);
            return true;
        }
        NickState current = records.read(player);
        if (current.version() != 0L) {
            return true;
        }
        if (namedByAnother(trimmed, player)) {
            log.info("event=legacy_nick_dropped player={} reason=taken", player);
            return true;
        }
        if (write(player, 0L, trimmed) instanceof NickWrite.Taken) {
            log.info("event=legacy_nick_dropped player={} reason=taken", player);
        }
        return true;
    }

    /**
     * {@code owner} joined under {@code name}: a nick another player wears that copies it, set while the name was free,
     * is taken away, so the name's own player is never passed for. Reads and writes the database: call it off the
     * tick, after the name is stored.
     */
    public void nameJoined(UUID owner, String name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        for (UUID holder : records.holders(name)) {
            if (!holder.equals(owner)) {
                release(holder, name);
            }
        }
    }

    private void release(UUID holder, String name) {
        String wanted = name.strip().toLowerCase(Locale.ROOT);
        for (int attempt = 0; attempt < RELEASE_ATTEMPTS; attempt++) {
            NickState current = records.read(holder);
            if (!current.nick().toLowerCase(Locale.ROOT).equals(wanted)) {
                return;
            }
            if (write(holder, current.version(), "") instanceof NickWrite.Written) {
                log.info("event=nick_released player={} name={}", holder, name);
                return;
            }
        }
    }

    /** Write over the row at {@code version}; a written nick is shown and announced, a refused one read again. */
    private NickWrite write(UUID player, long version, String nick) {
        NickWrite outcome = records.write(player, version, nick);
        if (outcome instanceof NickWrite.Written) {
            held.refresh(player);
            announce.accept(player);
        } else if (outcome instanceof NickWrite.Stale) {
            held.refresh(player);
        }
        return outcome;
    }

    /** Whether an account other than {@code owner} holds {@code nick} as its name, in any case. */
    private boolean namedByAnother(String nick, UUID owner) {
        for (PlayerName account : names.underName(nick, ACCOUNTS_READ)) {
            if (!account.uuid().equals(owner)) {
                return true;
            }
        }
        return false;
    }
}
