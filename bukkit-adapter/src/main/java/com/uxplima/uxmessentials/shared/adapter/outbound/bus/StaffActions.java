package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.RecentIds;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.StaffActionFrame;
import com.uxplima.uxmessentials.shared.network.StaffActionFrame.Action;
import org.jspecify.annotations.NullMarked;

/**
 * Carries a staff action to the backend holding its target, and a staff notice to every backend. The staff member's
 * backend checks the permission and the target and decides; the frame only asks the backend that holds the player's
 * connection to carry out the part nothing else can.
 *
 * <p>A listener takes the frames of one action that are for this backend, each once: with the proxy and Redis both
 * carrying frames, a second copy is dropped. Frames from this backend never reach a listener at all.
 */
@NullMarked
public final class StaffActions {

    /** The target a notice names, since it is about no one player. */
    private static final PlayerRef NOBODY = new PlayerRef(new UUID(0L, 0L), "-");

    private final BusPublisher bus;

    public StaffActions(BusPublisher bus) {
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    /** This backend's name on the network. */
    public String serverId() {
        return bus.serverId();
    }

    /** Ask {@code server} to carry out {@code action} on {@code target}, with {@code values} as its arguments. */
    public void send(Action action, String server, PlayerRef target, String actorName, Map<String, String> values) {
        Objects.requireNonNull(server, "server");
        bus.publish(frame(action, server, target, actorName, "", values));
    }

    /**
     * Show the line {@code key} with {@code values} to the staff on every other backend. A notice is about no one
     * player; the names it shows are among its values.
     */
    public void notice(String key, Map<String, String> values) {
        Objects.requireNonNull(key, "key");
        bus.publish(frame(Action.STAFF_NOTICE, "", NOBODY, "", key, values));
    }

    private StaffActionFrame frame(
            Action action, String server, PlayerRef target, String actorName, String key, Map<String, String> values) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        return new StaffActionFrame(
                bus.serverId(),
                UUID.randomUUID(),
                action.name(),
                server,
                target.uuid(),
                target.name(),
                actorName,
                key,
                values);
    }

    /** A listener handing {@code handler} each frame of {@code action} meant for {@code server}, once. */
    public static RemoteSyncListener listener(String server, Action action, Consumer<StaffActionFrame> handler) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(handler, "handler");
        RecentIds handled = new RecentIds();
        return message -> {
            if (message instanceof StaffActionFrame frame
                    && action.name().equals(frame.action())
                    && frame.isFor(server)
                    && handled.firstTime(frame.frameId())) {
                handler.accept(frame);
            }
        };
    }
}
