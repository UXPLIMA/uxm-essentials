package com.uxplima.uxmessentials.shared.adapter.inbound.ip;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one place a join's address is captured. It tokenises the connecting address and writes the
 * {@code (account, token)} association off the tick thread, then hands the token to whoever is watching
 * ({@link IpHistoryObserver}) so a reader never races the write it depends on.
 *
 * <p>Whether the raw address is retained alongside the token is decided at wiring time by the module that
 * consumes it: with moderation enabled the address is kept, because {@code /seenip} renders it and a STRICT ban
 * IP-bans every address a target is known to have used; with moderation off only the token is written, and the
 * server holds no raw addresses at all. The recorder itself is wired only when moderation or security is enabled,
 * so a server that runs neither records nothing.
 *
 * <p>The address is read on the tick thread (a {@link Player} lookup) and everything after it happens on the
 * async task, so the handler adds no tick-thread I/O.
 */
@NullMarked
public final class IpHistoryRecorder implements Listener {

    private final IpHistoryStore store;
    private final IpTokens tokens;
    private final Scheduler scheduler;
    private final Clock clock;
    private final boolean retainAddress;
    private final List<IpHistoryObserver> observers = new CopyOnWriteArrayList<>();

    public IpHistoryRecorder(
            IpHistoryStore store, IpTokens tokens, Scheduler scheduler, Clock clock, boolean retainAddress) {
        this.store = Objects.requireNonNull(store, "store");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.retainAddress = retainAddress;
    }

    /** Watch every recorded association. Registered at wiring time; the list is never mutated afterwards. */
    public void observe(IpHistoryObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String address = addressOf(player);
        if (address == null) {
            return;
        }
        PlayerRef who = BukkitRefs.toRef(player);
        scheduler.async(() -> record(who, address));
    }

    private void record(PlayerRef who, String address) {
        String token = tokens.tokenFor(address);
        store.record(who.uuid(), token, retainAddress ? address : null, clock.instant());
        observers.forEach(observer -> observer.onRecorded(who, token));
    }

    private static @Nullable String addressOf(Player player) {
        InetSocketAddress socket = player.getAddress();
        if (socket == null) {
            return null;
        }
        InetAddress address = socket.getAddress();
        return address == null ? null : address.getHostAddress();
    }
}
