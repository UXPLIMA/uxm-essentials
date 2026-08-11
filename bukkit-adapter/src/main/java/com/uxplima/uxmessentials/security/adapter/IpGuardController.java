package com.uxplima.uxmessentials.security.adapter;

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
import com.uxplima.uxmessentials.security.domain.AltLimitPolicy;
import com.uxplima.uxmessentials.shared.adapter.inbound.ip.IpHistoryObserver;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The same-IP alt guard's brain: once the kernel recorder has written a join's association, it decides whether the
 * address now carries more distinct accounts than the configured cap (kick if so) and whether the player shares an
 * address with other accounts (notify staff if so). It never sees a raw address, only the one-way token the
 * recorder hands it and the accounts seen on that token, and there is no GeoIP.
 *
 * <p>The guard captures nothing itself: it is an {@link IpHistoryObserver}, so the association is always written
 * before it reads. Its callback already runs off the tick thread (the recorder's async task); the kick hops back
 * onto the player's region thread. The whole guard is inert when {@code ip-guard.enabled} is false.
 */
@NullMarked
public final class IpGuardController implements IpHistoryObserver {

    private final IpHistoryStore store;
    private final SecurityConfig.IpGuard config;
    private final PlayerLookup lookup;
    private final SecurityStaffNotifier notifier;
    private final Scheduler scheduler;
    private final Messages messages;

    public IpGuardController(
            IpHistoryStore store,
            SecurityConfig.IpGuard config,
            PlayerLookup lookup,
            SecurityStaffNotifier notifier,
            Scheduler scheduler,
            Messages messages) {
        this.store = Objects.requireNonNull(store, "store");
        this.config = Objects.requireNonNull(config, "config");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Enforce the cap and the staff notice for the join the recorder has just written. */
    @Override
    public void onRecorded(PlayerRef ref, String ipToken) {
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(ipToken, "ipToken");
        if (!config.enabled()) {
            return;
        }
        guard(ref, ipToken);
    }

    private void guard(PlayerRef ref, String ipHash) {
        Set<UUID> onIp = store.accountsOnToken(ipHash);
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
}
