package com.uxplima.uxmessentials.security.adapter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.security.application.port.IpGuardStore;
import com.uxplima.uxmessentials.security.domain.AltLimitPolicy;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The same-IP alt guard's join brain: on join it records the player's hashed IP token, then decides — off the tick
 * thread — whether the address now carries more distinct accounts than the configured cap (kick if so) and whether
 * the player shares an address with other accounts (notify staff if so). No raw address is ever handled past the
 * hash, and there is no GeoIP — only the one-way token and the accounts seen on it.
 *
 * <p>The DB record and the two reads run off the tick thread through the injected {@link Scheduler}; the kick hops
 * back onto the player's region thread. The whole guard is inert when {@code ip-guard.enabled} is false.
 */
@NullMarked
public final class IpGuardController {

    private final IpGuardStore store;
    private final SecurityConfig.IpGuard config;
    private final PlayerLookup lookup;
    private final SecurityStaffNotifier notifier;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Clock clock;

    public IpGuardController(
            IpGuardStore store,
            SecurityConfig.IpGuard config,
            PlayerLookup lookup,
            SecurityStaffNotifier notifier,
            Scheduler scheduler,
            Messages messages,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.config = Objects.requireNonNull(config, "config");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Record {@code player}'s address off the tick thread and enforce the cap / staff notice for this join. */
    public void onJoin(Player player) {
        Objects.requireNonNull(player, "player");
        if (!config.enabled()) {
            return;
        }
        String ipHash = ipHash(player);
        if (ipHash == null) {
            return;
        }
        PlayerRef ref = BukkitRefs.toRef(player);
        scheduler.async(() -> guard(ref, ipHash));
    }

    private void guard(PlayerRef ref, String ipHash) {
        store.record(ref.uuid(), ipHash, clock.instant());
        Set<UUID> onIp = store.accountsOnIp(ipHash);
        Set<UUID> others =
                onIp.stream().filter(account -> !account.equals(ref.uuid())).collect(Collectors.toUnmodifiableSet());
        if (config.notifyStaff() && !others.isEmpty()) {
            announceAlts(ref, others);
        }
        AltLimitPolicy limit = config.limitPolicy();
        if (limit.evaluate(onIp.size()) == AltLimitPolicy.Decision.DENY) {
            scheduler.onEntity(ref, () -> kick(ref));
        }
    }

    private void announceAlts(PlayerRef ref, Set<UUID> others) {
        String names = others.stream().map(this::nameOf).collect(Collectors.joining(", "));
        Map<String, String> placeholders =
                Map.of("player", ref.name(), "alts", names, "count", Integer.toString(others.size()));
        notifier.notifyStaff(
                SecurityMessageKey.SECURITY_ALTS_NOTIFY,
                placeholders,
                "IP/alt guard: " + ref.name() + " shares an address with " + others.size() + " account(s): " + names);
    }

    private String nameOf(UUID account) {
        return lookup.findByUuid(account).map(PlayerRef::name).orElse(account.toString());
    }

    private void kick(PlayerRef ref) {
        Player live = Bukkit.getPlayer(ref.uuid());
        if (live != null && live.isOnline()) {
            live.kick(render(ref));
        }
    }

    private Component render(PlayerRef ref) {
        return StyledText.render(messages.resolve(ref, SecurityMessageKey.SECURITY_ALTS_KICKED, Map.of()));
    }

    private @Nullable String ipHash(Player player) {
        InetSocketAddress socket = player.getAddress();
        if (socket == null) {
            return null;
        }
        InetAddress address = socket.getAddress();
        return address == null ? null : IpHashing.hash(address.getHostAddress());
    }
}
