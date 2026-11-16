package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Carries the ban-on-login check across the two events a login passes through, so the server thread never waits on
 * storage. At pre-login, off the tick, it reads the player's {@link LoginEnforcement.Standing} and holds it. At login,
 * where the connecting {@code Player} has its permissions, it takes what it held and decides from it without reading
 * storage again. The last-seen row and the audit line are then written on the storage lane.
 *
 * <p>The check fails safe. A pre-login whose read fails, and a login that finds nothing held for it (a pre-login
 * another plugin refused and a later one allowed again, a hold that outlived {@link #HELD_FOR}, or a different
 * address than the one read), is refused with a line asking the player to join again: it is never let in unchecked,
 * and nothing is read from storage on the server thread to make up for it.
 *
 * <p>A hold is taken by the login it was read for. One that no login takes (the connection dropped, or another plugin
 * refused the pre-login) is dropped by {@link #forget}, and failing that the next time anything is held after it
 * expired, so the map never grows past the logins of the last {@link #HELD_FOR}.
 */
public final class LoginGate {

    /**
     * How long a pre-login read stands in for its login. A login follows its pre-login within moments, even when other
     * plugins make the pre-login wait a few seconds; a read older than this is too stale to let anybody in on.
     */
    public static final Duration HELD_FOR = Duration.ofSeconds(30);

    private record Held(LoginEnforcement.Standing standing, Instant readAt) {}

    private final LoginEnforcement enforcement;
    private final StorageLane lane;
    private final Notifier notifier;
    private final Logger log;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Held> held = new ConcurrentHashMap<>();

    public LoginGate(LoginEnforcement enforcement, StorageLane lane, Notifier notifier, Logger log, Clock clock) {
        this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Read and hold what the login of {@code who} from {@code ip} will be decided on. Call it at pre-login, off the
     * tick. Returns the disconnect screen when the read failed and the login must be refused now.
     */
    public Optional<String> preLogin(PlayerRef who, String ip) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(ip, "ip");
        // A hold left by an earlier attempt that never logged in must not stand in for this one.
        held.remove(who.uuid());
        LoginEnforcement.Standing standing;
        try {
            standing = enforcement.read(who, ip);
        } catch (RuntimeException failure) {
            log.warn(
                    "event=login_check_failed player={} reason={}; the login was refused so a banned player cannot"
                            + " slip in while storage is unreachable",
                    who.name(),
                    failure.toString());
            return Optional.of(unchecked(who));
        }
        Instant now = clock.instant();
        held.values().removeIf(entry -> expired(entry, now));
        held.put(who.uuid(), new Held(standing, now));
        return Optional.empty();
    }

    /** Drop what was held for {@code player}: their pre-login was refused or their connection closed. */
    public void forget(UUID player) {
        Objects.requireNonNull(player, "player");
        held.remove(player);
    }

    /**
     * Decide the login of {@code who} from {@code ip} on what their pre-login read. Call it at login, on the server
     * thread; it reads no storage. {@code hasBypass} and {@code exempt} are the live permission answers
     * {@link LoginEnforcement#decide} takes.
     */
    public LoginEnforcement.Decision login(PlayerRef who, String ip, boolean hasBypass, boolean exempt) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(ip, "ip");
        Held entry = held.remove(who.uuid());
        if (entry == null
                || expired(entry, clock.instant())
                || !entry.standing().ip().equals(ip)) {
            log.warn(
                    "event=login_unchecked player={} reason={}; the login was refused and the player asked to join"
                            + " again",
                    who.name(),
                    entry == null ? "no pre-login read" : "stale pre-login read");
            return LoginEnforcement.Decision.retryLater(unchecked(who));
        }
        LoginEnforcement.Standing standing = entry.standing();
        LoginEnforcement.Decision decision = enforcement.decide(standing, hasBypass, exempt);
        lane.background(who, () -> enforcement.record(standing, !decision.allowed()));
        return decision;
    }

    private String unchecked(PlayerRef who) {
        return notifier.render(who, ModerationMessageKey.MOD_LOGIN_UNCHECKED, Map.of());
    }

    private static boolean expired(Held entry, Instant now) {
        return entry.readAt().plus(HELD_FOR).isBefore(now);
    }
}
