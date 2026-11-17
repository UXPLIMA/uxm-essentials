package com.uxplima.uxmessentials.communication.application;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.communication.domain.ServerSwitchPolicy;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;

/**
 * Tells a player moving between the servers of a network apart from one joining or leaving it, so the join and quit
 * lines and the MOTD are not shown for every move, and counts the players {@code {count}} names.
 *
 * <p>A join is a move when the player is still online on another server, or left one within the window: the proxy
 * connects a player to their next server before it lets go of the last one, so the next server usually still sees
 * them there. A quit cannot know yet where the player is going, so its line waits for the window and is shown only when
 * the player turned up on no server by then. On a single server nothing is a move and nothing waits.
 */
public final class ServerSwitches {

    private final NetworkPresence presence;
    private final Supplier<ServerSwitchPolicy> policy;
    private final BooleanSupplier networked;

    /** @param networked whether this server is part of a network ({@code network.enabled}) */
    public ServerSwitches(NetworkPresence presence, Supplier<ServerSwitchPolicy> policy, BooleanSupplier networked) {
        this.presence = Objects.requireNonNull(presence, "presence");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.networked = Objects.requireNonNull(networked, "networked");
    }

    /** Whether {@code player}, joining here, moved from another server and their join shows nothing. */
    public boolean hidesJoin(UUID player) {
        Objects.requireNonNull(player, "player");
        ServerSwitchPolicy now = policy.get();
        return hides(now) && presence.cameFrom(player, now.window()).isPresent();
    }

    /**
     * Whether a quit line waits to learn if the player moved to another server; it waits {@link #quitWait()}. A zero
     * window shows it at once.
     */
    public boolean holdsQuit() {
        ServerSwitchPolicy now = policy.get();
        return hides(now) && !now.window().isZero();
    }

    /** How long a held quit line waits. */
    public Duration quitWait() {
        return policy.get().window();
    }

    /** Whether the held quit line of {@code player} is shown: they are on no server now, here included. */
    public boolean showsHeldQuit(UUID player) {
        return presence.find(Objects.requireNonNull(player, "player")).isEmpty();
    }

    /**
     * The {@code {count}} for a line about {@code player}: {@code serverCount}, the players on this server, or with the
     * network count on every player of the network, counting the player in while they join and out as they quit.
     */
    public int count(int serverCount, UUID player, boolean joining) {
        Objects.requireNonNull(player, "player");
        if (!networked.getAsBoolean() || !policy.get().networkCount()) {
            return serverCount;
        }
        Set<UUID> everyone = new HashSet<>();
        for (OnlinePlayer online : presence.online()) {
            everyone.add(online.player().uuid());
        }
        if (joining) {
            everyone.add(player);
        } else {
            everyone.remove(player);
        }
        return everyone.size();
    }

    private boolean hides(ServerSwitchPolicy now) {
        return networked.getAsBoolean() && now.hideSwitches();
    }
}
