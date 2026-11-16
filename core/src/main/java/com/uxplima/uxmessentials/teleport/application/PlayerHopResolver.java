package com.uxplima.uxmessentials.teleport.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopNotice;
import com.uxplima.uxmessentials.shared.application.hop.HopNotices;
import com.uxplima.uxmessentials.shared.application.hop.HopResolution;
import com.uxplima.uxmessentials.shared.application.hop.HopResolver;
import com.uxplima.uxmessentials.shared.application.hop.HopSettlement;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmessentials.teleport.application.port.RequestVisibility;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.TeleportMessageKey;

/**
 * The landing side of a hop to a player: an accepted request ({@link HopKind#PLAYER_REQUEST}) or a staff teleport
 * ({@link HopKind#PLAYER_STAFF}). The anchor player must still be online on this backend; their position is read
 * at landing time on their own entity thread, so the mover arrives where the anchor stands now rather than where
 * they stood when the hop was sent. An accepted request lands through the teleport engine's immediate arrival, which
 * applies the jail, combat and veto checks and stamps the request cooldown, as a local accepted request does. A staff
 * teleport lands through the executor as {@link TeleportKind#ADMIN}, exactly as a local staff {@code /tp} moves a
 * player: no check, no cooldown. A staff hop also lands only while the anchor is still visible at the see level of
 * the staff member who ordered it, judged by this backend's vanish state; an anchor who vanished from them since reads
 * as unreachable.
 *
 * <p>A hop to a player carries no charge, so there is nothing to refund or settle; every way the landing can fail
 * (the anchor left, the mover left, a refusal, a shutdown) is a miss, which tells the mover once.
 */
public final class PlayerHopResolver implements HopResolver {

    private static final HopNotices REQUEST_NOTICES = noticesWith(SharedMessageKey.COMMON_HOP_BUSY);
    // A staff teleport moves a player who did not ask to go, so a hop turned away for another move under way gives
    // them no advice to try again.
    private static final HopNotices STAFF_NOTICES = noticesWith(SharedMessageKey.COMMON_HOP_BUSY_ORDERED);

    private final HopKind kind;
    private final TeleportEngine engine;
    private final TeleportExecutor executor;
    private final RequestVisibility visibility;
    private final PlayerLookup players;
    private final PlayerLocator locator;
    private final Scheduler scheduler;

    public PlayerHopResolver(
            HopKind kind,
            TeleportEngine engine,
            TeleportExecutor executor,
            RequestVisibility visibility,
            PlayerLookup players,
            PlayerLocator locator,
            Scheduler scheduler) {
        this.kind = Objects.requireNonNull(kind, "kind");
        if (kind != HopKind.PLAYER_REQUEST && kind != HopKind.PLAYER_STAFF) {
            throw new IllegalArgumentException("not a hop to a player: " + kind);
        }
        this.engine = Objects.requireNonNull(engine, "engine");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.players = Objects.requireNonNull(players, "players");
        this.locator = Objects.requireNonNull(locator, "locator");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public HopKind kind() {
        return kind;
    }

    @Override
    public HopNotices notices() {
        return kind == HopKind.PLAYER_STAFF ? STAFF_NOTICES : REQUEST_NOTICES;
    }

    private static HopNotices noticesWith(MessageKey busy) {
        return new HopNotices(
                "player",
                Map.of(
                        HopNotice.SENDING, TeleportMessageKey.CROSS_SERVER_SENDING,
                        HopNotice.ARRIVED, TeleportMessageKey.CROSS_SERVER_ARRIVED,
                        HopNotice.TIMED_OUT, TeleportMessageKey.CROSS_SERVER_TIMED_OUT,
                        HopNotice.MISROUTED, TeleportMessageKey.CROSS_SERVER_MISROUTED,
                        HopNotice.FAILED, TeleportMessageKey.CROSS_SERVER_FAILED,
                        HopNotice.UNAVAILABLE, TeleportMessageKey.CROSS_SERVER_UNAVAILABLE,
                        HopNotice.BUSY, busy,
                        HopNotice.REFUNDED, TeleportMessageKey.CROSS_SERVER_REFUNDED));
    }

    @Override
    public String label(PendingHop hop) {
        return PlayerHops.anchorOf(hop.target()).map(PlayerRef::name).orElse(hop.target());
    }

    @Override
    public HopResolution resolve(PendingHop hop) {
        Optional<PlayerRef> anchor = PlayerHops.anchorOf(hop.target());
        String label = label(hop);
        if (anchor.isEmpty() || !players.isOnline(anchor.get().uuid()) || hidden(hop, anchor.get())) {
            return HopResolution.unreachable(label);
        }
        PlayerRef found = anchor.get();
        return HopResolution.landsAt(label, (who, onLanded, onMissed) -> land(who, found, onLanded, onMissed));
    }

    /**
     * Read the anchor's position on their thread, then move the mover on theirs. Each step has a retired branch, so
     * an anchor or mover who leaves in between, or a backend that stops, ends the claimed hop as a miss.
     */
    private void land(PlayerRef who, PlayerRef anchor, Runnable onLanded, Runnable onMissed) {
        scheduler.onEntity(
                anchor,
                () -> {
                    Optional<Position> standing = locator.locate(anchor);
                    if (standing.isEmpty()) {
                        onMissed.run();
                        return;
                    }
                    try {
                        scheduler.onEntity(who, () -> arrive(who, standing.get(), onLanded, onMissed), onMissed);
                    } catch (RuntimeException unscheduled) {
                        onMissed.run();
                    }
                },
                onMissed);
    }

    /** Whether the staff member who ordered {@code hop} may no longer see its anchor here. */
    private boolean hidden(PendingHop hop, PlayerRef anchor) {
        int seeLevel = PlayerHops.seeLevelOf(hop.target());
        return seeLevel != RequestVisibility.SEES_EVERYONE
                && visibility.hides(anchor, new PlayerRef(hop.player(), anchor.name()), seeLevel);
    }

    private void arrive(PlayerRef who, Position standing, Runnable onLanded, Runnable onMissed) {
        if (kind == HopKind.PLAYER_STAFF) {
            executor.teleport(who, Destination.at(standing), TeleportKind.ADMIN, onLanded, onMissed);
            return;
        }
        Result<Unit, TeleportError> issued =
                engine.arriveImmediately(who, Destination.at(standing), TeleportKind.REQUEST, onLanded, onMissed);
        // A refusal has told the player why and runs neither callback, so the hop is ended here.
        if (issued.isErr()) {
            onMissed.run();
        }
    }

    @Override
    public boolean refund(PlayerRef who, HopCharge charge) {
        // Never asked: a hop to a player is sent without a charge.
        return false;
    }

    @Override
    public boolean settle(PlayerRef who, HopSettlement settlement, HopCharge charge) {
        // Never asked: a hop to a player is sent without a settlement.
        return false;
    }
}
