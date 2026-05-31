package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Delivers one resolved announcer line to every online player who has not opted out via {@code /broadcasttoggle}.
 * The line is operator-authored content — a raw MiniMessage source string the {@link MessageSink} parses into a
 * Component once per viewer — so it never flows through the {@code MessageKey} catalog and is never parity-checked.
 *
 * <p>The fan-out reads the live online set and the {@link BroadcastOptOutStore} opt-out bit per player. An
 * announcer tick is infrequent (one every configured interval), so the per-tick scan of the online set is
 * acceptable. Delivery hops to each viewer's region thread inside the sink and silently no-ops a player who
 * dropped between the scan and the send.
 */
@NullMarked
public final class BukkitAnnouncerBroadcaster {

    private final MessageSink sink;
    private final BroadcastOptOutStore optOut;

    public BukkitAnnouncerBroadcaster(MessageSink sink, BroadcastOptOutStore optOut) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.optOut = Objects.requireNonNull(optOut, "optOut");
    }

    /** Send {@code line} to every online, opted-in player. */
    public void broadcast(String line) {
        Objects.requireNonNull(line, "line");
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerRef who = BukkitRefs.toRef(player);
            if (optOut.receivesBroadcasts(who)) {
                sink.deliver(who, line);
            }
        }
    }

    /** The current online player count the announcer's min-players gate is checked against. */
    public int onlineCount() {
        return Bukkit.getOnlinePlayers().size();
    }
}
