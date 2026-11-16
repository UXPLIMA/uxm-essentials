package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.moderation.application.port.FreezeSync;
import com.uxplima.uxmessentials.moderation.application.port.JailSync;
import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.BanChanged;
import com.uxplima.uxmessentials.shared.network.FreezeChanged;
import com.uxplima.uxmessentials.shared.network.IpBanChanged;
import com.uxplima.uxmessentials.shared.network.JailChanged;
import com.uxplima.uxmessentials.shared.network.JailHopFailed;
import com.uxplima.uxmessentials.shared.network.MuteChanged;
import org.jspecify.annotations.NullMarked;

/**
 * The moderation context's cross-server seam. The same two halves as {@link VaultSync}, but with no
 * repository decorator and no cache: a ban/mute is already enforced durably across the cluster by the shared
 * DB (every backend re-reads it on the next login), so this carries only the <em>live</em> effect.
 *
 * <ul>
 *   <li><b>Outbound</b>: {@link #publisher(BusPublisher)} binds the bus-agnostic {@link SanctionSync} port to
 *       the bus, so a successful {@code /ban}/{@code /tempban} publishes a {@link BanChanged} and a successful
 *       {@code /mute} publishes a {@link MuteChanged}, and a successful {@code /banip}/{@code /tempbanip} an
 *       {@link IpBanChanged}, each stamped with this backend's origin.
 *   <li><b>Inbound</b>: {@link #listener(Consumer, Consumer, Consumer)} returns a {@link RemoteSyncListener} that, on a
 *       remote {@code BanChanged}, hands the target UUID to the wired re-enforcement action (the
 *       {@code EnforceRemoteBan} use case) which re-evaluates that target's ban here and kicks them if it is in
 *       effect and they are online, and on a remote {@code IpBanChanged} hands the address to
 *       {@code EnforceRemoteIpBan}, which kicks everyone here on it. A remote {@code MuteChanged} hands the target
 *       to the in-memory mute of connected players, which reads it again.
 * </ul>
 *
 * <p>A jail is live too: {@link #jailPublisher(BusPublisher)} announces a jail or release and a jail hop that
 * failed, and {@link #jailListener} hands them to the use cases that apply the change to a player connected here
 * and tell this backend's staff. Each jail frame carries an id, so a copy delivered twice (the proxy and Redis both
 * carry it) is acted on once.
 *
 * <p>A freeze is held in memory by each backend, so {@link #freezePublisher(BusPublisher)} announces a freeze or
 * release and {@link #freezeListener} hands a peer's to the use case that holds it here too, so a frozen player who
 * switches servers arrives frozen. Each freeze frame carries an id and the time of the change, so a copy acts once
 * and an older change cannot undo a newer one.
 *
 * <p>Self-origin frames never reach the listener. The {@link BusCore} drops any frame whose origin equals
 * this backend's id before dispatch, so the player the local {@code /ban} already kicked is never kicked
 * twice. With the bus disabled the publisher is {@link SanctionSync#NONE} and the listener is never invoked, so
 * the single-server path is unchanged.
 */
@NullMarked
public final class ModerationSync {

    private ModerationSync() {}

    /** A {@link SanctionSync} that publishes a ban/mute frame on the bus after a successful local sanction. */
    public static SanctionSync publisher(BusPublisher bus) {
        Objects.requireNonNull(bus, "bus");
        return new Publishing(bus);
    }

    /**
     * A listener that hands a remote {@link BanChanged}'s target to {@code onRemoteBan} (the re-enforcement
     * action that kicks an online, still-banned target) and a remote {@link IpBanChanged}'s address to
     * {@code onRemoteIpBan} (which kicks everyone here on that address), and a remote {@link MuteChanged}'s target to
     * {@code onRemoteMute} (which reads that player's mute again). The action is a
     * {@code Consumer<UUID>} so the wiring passes {@code enforce::onRemoteBan} and this seam stays trivially
     * testable without the use case's port graph.
     */
    public static RemoteSyncListener listener(
            Consumer<UUID> onRemoteBan, Consumer<String> onRemoteIpBan, Consumer<UUID> onRemoteMute) {
        Objects.requireNonNull(onRemoteBan, "onRemoteBan");
        Objects.requireNonNull(onRemoteIpBan, "onRemoteIpBan");
        Objects.requireNonNull(onRemoteMute, "onRemoteMute");
        return message -> {
            if (message instanceof BanChanged changed) {
                onRemoteBan.accept(changed.target());
            } else if (message instanceof IpBanChanged ipBan) {
                onRemoteIpBan.accept(ipBan.ip());
            } else if (message instanceof MuteChanged mute) {
                onRemoteMute.accept(mute.target());
            }
        };
    }

    /** A {@link JailSync} that announces jail changes and failed jail hops on the bus, each under a fresh id. */
    public static JailSync jailPublisher(BusPublisher bus) {
        Objects.requireNonNull(bus, "bus");
        return new JailPublishing(bus);
    }

    /** How a backend reacts to a peer's jail frames: the two use case entry points, kept as plain callbacks. */
    public interface JailFrames {

        /** A peer jailed ({@code jailed}) or released {@code target}. */
        void jailChanged(UUID frameId, UUID target, boolean jailed);

        /** A peer could not take {@code target}, named {@code targetName}, to {@code jail}. */
        void hopFailed(UUID frameId, UUID target, String targetName, String jail);
    }

    /** A listener that hands a peer's {@link JailChanged} and {@link JailHopFailed} frames to {@code frames}. */
    public static RemoteSyncListener jailListener(JailFrames frames) {
        Objects.requireNonNull(frames, "frames");
        return message -> {
            if (message instanceof JailChanged changed) {
                frames.jailChanged(changed.frameId(), changed.target(), changed.jailed());
            } else if (message instanceof JailHopFailed failed) {
                frames.hopFailed(failed.frameId(), failed.target(), failed.targetName(), failed.jail());
            }
        };
    }

    /** A {@link FreezeSync} that announces a freeze or release on the bus under a fresh id. */
    public static FreezeSync freezePublisher(BusPublisher bus) {
        Objects.requireNonNull(bus, "bus");
        return (target, frozen, at) -> bus.publish(new FreezeChanged(
                bus.serverId(), UUID.randomUUID(), target.uuid(), target.name(), frozen, at.toEpochMilli()));
    }

    /** How a backend takes a peer's freeze or release, kept as a plain callback. */
    @FunctionalInterface
    public interface FreezeFrames {

        /** A peer froze ({@code frozen}) or released {@code target} at {@code at}. */
        void freezeChanged(UUID frameId, PlayerRef target, boolean frozen, java.time.Instant at);
    }

    /** A listener that hands a peer's {@link FreezeChanged} frames to {@code frames}. */
    public static RemoteSyncListener freezeListener(FreezeFrames frames) {
        Objects.requireNonNull(frames, "frames");
        return message -> {
            if (message instanceof FreezeChanged changed) {
                frames.freezeChanged(
                        changed.frameId(),
                        new PlayerRef(changed.target(), changed.targetName()),
                        changed.frozen(),
                        java.time.Instant.ofEpochMilli(changed.changedAtMillis()));
            }
        };
    }

    private record JailPublishing(BusPublisher bus) implements JailSync {

        @Override
        public void jailChanged(PlayerRef target, boolean jailed) {
            bus.publish(new JailChanged(bus.serverId(), UUID.randomUUID(), target.uuid(), jailed));
        }

        @Override
        public void hopFailed(PlayerRef target, String jail) {
            bus.publish(new JailHopFailed(bus.serverId(), UUID.randomUUID(), target.uuid(), target.name(), jail));
        }
    }

    /** Stamps each sanction with this backend's origin and routes the matching frame to peers. */
    private record Publishing(BusPublisher bus) implements SanctionSync {

        @Override
        public void banChanged(PlayerRef target) {
            bus.publish(new BanChanged(bus.serverId(), target.uuid()));
        }

        @Override
        public void muteChanged(PlayerRef target) {
            bus.publish(new MuteChanged(bus.serverId(), target.uuid()));
        }

        @Override
        public void ipBanChanged(String ip) {
            bus.publish(new IpBanChanged(bus.serverId(), ip));
        }
    }
}
