package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.moderation.application.JailCountdown;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The jail/seen lifecycle listener — the online-only jail countdown's connection edges (docs/02-concurrency,
 * docs/09-deployment):
 *
 * <ul>
 *   <li><b>Join</b> — record last-seen/last-IP, then re-apply a still-active jail (offline jail re-applied so
 *       a player cannot escape by relogging) and stamp the session start. Nothing is decremented yet.
 *   <li><b>Quit</b> — burn the online time elapsed since the session start off an online-only sentence
 *       (frozen while offline), persisting the decremented remainder or releasing a served sentence, then
 *       record last-seen and drop the session stamp.
 * </ul>
 *
 * <p>The per-player session-start stamps are transient in-memory state owned here ({@code
 * ConcurrentHashMap}), cleared on stop. The login ban-enforcement is a separate HIGHEST {@code
 * PlayerLoginEvent} listener; this one runs on join (after the connection is allowed).
 */
@NullMarked
public final class ModerationJoinListener implements Listener {

    private final JailCountdown countdown;
    private final ModerationRepository repository;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Instant> sessionStart = new ConcurrentHashMap<>();

    public ModerationJoinListener(JailCountdown countdown, ModerationRepository repository, Clock clock) {
        this.countdown = Objects.requireNonNull(countdown, "countdown");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        Instant now = clock.instant();
        repository.recordSeen(who, ip(event), now);
        countdown.onJoin(who);
        sessionStart.put(who.uuid(), now);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        Instant now = clock.instant();
        Instant start = sessionStart.remove(who.uuid());
        if (start != null) {
            countdown.onTick(who, Duration.between(start, now));
        }
        repository.recordSeen(who, ip(event), now);
    }

    /** Drop every session stamp, called on module stop so no session state survives. */
    public void clear() {
        sessionStart.clear();
    }

    private static Optional<String> ip(PlayerJoinEvent event) {
        var address = event.getPlayer().getAddress();
        return address == null
                ? Optional.empty()
                : Optional.ofNullable(address.getAddress()).map(java.net.InetAddress::getHostAddress);
    }

    private static Optional<String> ip(PlayerQuitEvent event) {
        var address = event.getPlayer().getAddress();
        return address == null
                ? Optional.empty()
                : Optional.ofNullable(address.getAddress()).map(java.net.InetAddress::getHostAddress);
    }
}
