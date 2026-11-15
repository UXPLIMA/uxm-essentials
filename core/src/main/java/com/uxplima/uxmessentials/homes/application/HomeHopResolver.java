package com.uxplima.uxmessentials.homes.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeSafetyChecker;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopNotice;
import com.uxplima.uxmessentials.shared.application.hop.HopNotices;
import com.uxplima.uxmessentials.shared.application.hop.HopResolution;
import com.uxplima.uxmessentials.shared.application.hop.HopResolver;
import com.uxplima.uxmessentials.shared.application.hop.HopSettlement;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;

/**
 * The home side of the shared cross-server hop. A hop's target is the home's owner and slot. On the target the home
 * is read again and moved to only while it still exists and still lives on this backend.
 *
 * <p>When the owner travels to their own home, the checks a local teleport from the home menu runs are run here, where
 * the home's world is: the claim access check, and the unsafe destination check while {@code confirm-unsafe-teleport}
 * is on (there is no one to confirm with on a landing, so an unsafe home is refused unless the player may bypass it).
 * A visit or a staff teleport skips them, as it does locally. The move is the teleport context's immediate arrival,
 * through {@link HomeTeleporter#arrive}, which stamps the home cooldown the origin checked.
 *
 * <p>A refund is paid through this backend's {@link HomeEconomy}, whether or not this backend charges for homes itself,
 * so the economy module must be on wherever a home hop can land. A home's teleport cost is the server's, so a hop to
 * one never carries a settlement.
 */
public final class HomeHopResolver implements HopResolver {

    /** Lets a player land at a home whose spot fails the safety check, as it skips the warning on a local teleport. */
    static final String BYPASS_UNSAFE = "uxmessentials.home.bypass.unsafe";

    private static final HopNotices NOTICES = new HopNotices(
            "home",
            Map.of(
                    HopNotice.SENDING, HomesMessageKey.HOME_CROSS_SERVER_SENDING,
                    HopNotice.ARRIVED, HomesMessageKey.HOME_CROSS_SERVER_ARRIVED,
                    HopNotice.TIMED_OUT, HomesMessageKey.HOME_CROSS_SERVER_TIMED_OUT,
                    HopNotice.MISROUTED, HomesMessageKey.HOME_CROSS_SERVER_MISROUTED,
                    HopNotice.FAILED, HomesMessageKey.HOME_CROSS_SERVER_FAILED,
                    HopNotice.UNAVAILABLE, HomesMessageKey.HOME_CROSS_SERVER_UNAVAILABLE,
                    HopNotice.REFUNDED, HomesMessageKey.HOME_CROSS_SERVER_REFUNDED));

    private final HomeRepository repository;
    private final HomeTeleporter teleporter;
    private final Optional<HomeEconomy> economy;
    private final HomeSafetyChecker safety;
    private final ClaimService claims;
    private final boolean guardUnsafe;
    private final Permissions permissions;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final String localServer;

    public HomeHopResolver(
            HomeRepository repository,
            HomeTeleporter teleporter,
            Optional<HomeEconomy> economy,
            HomeSafetyChecker safety,
            ClaimService claims,
            boolean guardUnsafe,
            Permissions permissions,
            Notifier notifier,
            Scheduler scheduler,
            String localServer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.safety = Objects.requireNonNull(safety, "safety");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.guardUnsafe = guardUnsafe;
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.localServer = Objects.requireNonNull(localServer, "localServer");
    }

    @Override
    public HopKind kind() {
        return HopKind.HOME;
    }

    @Override
    public HopNotices notices() {
        return NOTICES;
    }

    @Override
    public String label(PendingHop hop) {
        Optional<Key> key = Key.parse(hop.target());
        if (key.isEmpty()) {
            return hop.target();
        }
        return find(key.get()).map(HomeHops::labelOf).orElseGet(() -> key.get().slotNumber());
    }

    @Override
    public HopResolution resolve(PendingHop hop) {
        Optional<Key> key = Key.parse(hop.target());
        if (key.isEmpty()) {
            return HopResolution.unreachable(hop.target());
        }
        Optional<Home> found = find(key.get());
        if (found.isEmpty() || !isHere(found.get())) {
            return HopResolution.unreachable(
                    found.map(HomeHops::labelOf).orElseGet(() -> key.get().slotNumber()));
        }
        Home home = found.get();
        return HopResolution.landsAt(
                HomeHops.labelOf(home), (who, onLanded, onMissed) -> land(who, home, onLanded, onMissed));
    }

    private Optional<Home> find(Key key) {
        // Only the uuid identifies the owner to the store; the name is never read back.
        return repository.findSlot(new PlayerRef(key.owner(), key.owner().toString()), key.slot());
    }

    /**
     * Judge the destination on its own region thread, the only one that may read its blocks and claims, then move the
     * player on theirs. The hop is already claimed by now, so every way the move can fail to happen is a miss: a world
     * that is not loaded here or a backend that is stopping (the region task can never run), a player who left, or a
     * failure on the way. The claimed hop always ends in exactly one outcome.
     */
    private void land(PlayerRef who, Home home, Runnable onLanded, Runnable onMissed) {
        scheduler.onRegion(
                home.location(),
                () -> {
                    Optional<MessageKey> refusal = refusal(who, home);
                    try {
                        scheduler.onEntity(
                                who, () -> arriveUnlessRefused(who, home, refusal, onLanded, onMissed), onMissed);
                    } catch (RuntimeException unscheduled) {
                        onMissed.run();
                    }
                },
                onMissed);
    }

    /**
     * On the destination's region thread: the line that refuses the landing, or empty when it may go ahead. Only the
     * owner's own teleport is checked, matching the home menu; a visit and a staff teleport are not checked locally
     * either.
     */
    private Optional<MessageKey> refusal(PlayerRef who, Home home) {
        if (!who.equals(home.owner())) {
            return Optional.empty();
        }
        try {
            ClaimDecision access = claims.canAccess(who, home.location());
            if (!access.allowed()) {
                return Optional.of(claimKey(access));
            }
            if (guardUnsafe && safety.isUnsafe(home.location()) && !permissions.has(who, BYPASS_UNSAFE)) {
                return Optional.of(HomesMessageKey.HOME_CROSS_SERVER_UNSAFE);
            }
            return Optional.empty();
        } catch (RuntimeException unreadable) {
            // A destination that cannot be judged is refused: the player is refunded, not dropped into it.
            return Optional.of(HomesMessageKey.HOME_CROSS_SERVER_FAILED);
        }
    }

    private void arriveUnlessRefused(
            PlayerRef who, Home home, Optional<MessageKey> refusal, Runnable onLanded, Runnable onMissed) {
        if (refusal.isPresent()) {
            // The failed notice follows from the miss, so a refusal that is the failed line itself is not sent twice.
            if (refusal.get() != HomesMessageKey.HOME_CROSS_SERVER_FAILED) {
                notifier.send(who, refusal.get(), Map.of("home", HomeHops.labelOf(home)));
            }
            onMissed.run();
            return;
        }
        teleporter.arrive(who, home, onLanded, onMissed);
    }

    private static MessageKey claimKey(ClaimDecision decision) {
        return switch (decision) {
            case DENIED_FOREIGN -> HomesMessageKey.HOME_CLAIM_FOREIGN;
            case DENIED_REQUIRED -> HomesMessageKey.HOME_CLAIM_REQUIRED;
            case DENIED_TOO_CLOSE -> HomesMessageKey.HOME_CLAIM_TOO_CLOSE;
            case DENIED_ACCESS, ALLOWED -> HomesMessageKey.HOME_CLAIM_ACCESS_DENIED;
        };
    }

    @Override
    public boolean refund(PlayerRef who, HopCharge charge) {
        return economy.isPresent() && economy.get().refund(who, charge.amount(), charge.currencyId());
    }

    @Override
    public boolean settle(PlayerRef who, HopSettlement settlement, HopCharge charge) {
        // A home hop is sent without a settlement, so this is never asked; answering false hands the charge back
        // should a row ever carry one.
        return false;
    }

    /** A home moved to another backend since the hop was sent can no longer be landed at from here. */
    private boolean isHere(Home home) {
        return home.knownServer().map(localServer::equals).orElse(false);
    }

    /** A hop target read back: the home's owner and slot. */
    private record Key(UUID owner, HomeSlot slot) {

        static Optional<Key> parse(String target) {
            int colon = target.lastIndexOf(':');
            if (colon <= 0) {
                return Optional.empty();
            }
            try {
                UUID owner = UUID.fromString(target.substring(0, colon));
                int index = Integer.parseInt(target.substring(colon + 1));
                return index < 0 ? Optional.empty() : Optional.of(new Key(owner, HomeSlot.of(index)));
            } catch (IllegalArgumentException malformed) {
                // A row this release cannot read lands nowhere, and its charge goes back.
                return Optional.empty();
            }
        }

        String slotNumber() {
            return Integer.toString(slot.displayNumber());
        }
    }
}
