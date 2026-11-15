package com.uxplima.uxmessentials.economy.adapter.inbound.listener;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.application.ClaimDailyReward;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Credits a configurable daily login reward the first time a player joins each cooldown window. The "last
 * claimed" stamp is transient per-holder state, so it lives in the player's PDC under a single pre-created key
 * (never minted on the hot path): exactly the kind of cooldown stamp PDC is for. The PDC read/stamp runs on the
 * join (entity) thread where the player is safe to touch; the credit and the confirmation are bridged off-tick
 * through the kernel {@link Scheduler}, so the ledger write never blocks the join. Disabled by default and a
 * no-op for a non-positive amount.
 *
 * <p>On a network ({@code network}, present when {@code network.enabled} is on) the PDC is per backend, so the
 * stamp is taken in the shared database instead, off the tick, and only the backend that took it pays. The PDC
 * stamp this server still holds is read on the join thread and folded in, so switching the network on does not pay
 * everybody again.
 */
@NullMarked
public final class DailyRewardListener implements Listener {

    private final EconomyProvider economy;
    private final EconomyNotifier notifier;
    private final Scheduler scheduler;
    private final Currency currency;
    private final boolean enabled;
    private final BigDecimal amount;
    private final long cooldownMillis;
    private final NamespacedKey claimKey;
    private final Optional<ClaimDailyReward> network;

    public DailyRewardListener(
            Plugin plugin,
            EconomyProvider economy,
            EconomyNotifier notifier,
            Scheduler scheduler,
            Currency currency,
            boolean enabled,
            BigDecimal amount,
            Duration cooldown,
            Optional<ClaimDailyReward> network) {
        Objects.requireNonNull(plugin, "plugin");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.enabled = enabled;
        this.amount = Objects.requireNonNull(amount, "amount");
        this.cooldownMillis = Objects.requireNonNull(cooldown, "cooldown").toMillis();
        this.claimKey = new NamespacedKey(plugin, "daily_reward_claim");
        this.network = Objects.requireNonNull(network, "network");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled || amount.signum() <= 0) {
            return;
        }
        Player player = event.getPlayer();
        if (network.isPresent()) {
            payIfClaimed(player, network.get());
            return;
        }
        long now = System.currentTimeMillis();
        Long last = player.getPersistentDataContainer().get(claimKey, PersistentDataType.LONG);
        if (last != null && now - last < cooldownMillis) {
            return;
        }
        player.getPersistentDataContainer().set(claimKey, PersistentDataType.LONG, now);
        PlayerRef ref = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler.async(() -> pay(ref));
    }

    private void payIfClaimed(Player player, ClaimDailyReward claim) {
        Long local = player.getPersistentDataContainer().get(claimKey, PersistentDataType.LONG);
        OptionalLong localStamp = local == null ? OptionalLong.empty() : OptionalLong.of(local);
        PlayerRef ref = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler.async(() -> {
            if (claim.claim(ref.uuid(), localStamp)) {
                pay(ref);
            }
        });
    }

    /** Credit the reward and confirm it; runs on the async lane. */
    private void pay(PlayerRef ref) {
        Money reward = Money.of(currency, amount);
        economy.ensureAccount(ref, currency);
        if (economy.credit(ref, reward).isOk()) {
            notifier.send(ref, EconomyMessageKey.DAILY_REWARD_GRANTED, Map.of("amount", notifier.amount(reward)));
        }
    }
}
