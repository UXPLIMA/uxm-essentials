package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.shared.application.message.Notifier;

/**
 * The live reaction to an IP ban issued on another backend: re-read the ban for that address from the shared
 * database and, while it is in effect, kick every player connected here from it. The database, not the frame,
 * decides: an address unbanned between the frame and this read keeps its players.
 *
 * <p>Runs on the bus thread, so it touches nothing Bukkit itself: the database read is safe off the tick
 * thread, and {@link Sanctions#kickAddress} enumerates and kicks players on the threads that own them. The
 * backend that issued the ban never sees its own frame (the bus drops self-origin frames), so nobody is kicked
 * twice. Exempt holders are spared, as everywhere else an IP ban applies.
 */
public final class EnforceRemoteIpBan {

    private final ModerationRepository repository;
    private final Sanctions sanctions;
    private final ModerationGuard guard;
    private final Notifier notifier;
    private final Clock clock;

    public EnforceRemoteIpBan(
            ModerationRepository repository,
            Sanctions sanctions,
            ModerationGuard guard,
            Notifier notifier,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Kick everyone here on {@code ip} if its ban is in effect; a no-op when nobody is connected from it. */
    public void onRemoteIpBan(String ip) {
        Objects.requireNonNull(ip, "ip");
        repository
                .activeIpBan(ip, clock.instant())
                .ifPresent(ban -> IpBanKick.kickEveryoneOn(ban, Optional.empty(), guard, sanctions, notifier));
    }
}
