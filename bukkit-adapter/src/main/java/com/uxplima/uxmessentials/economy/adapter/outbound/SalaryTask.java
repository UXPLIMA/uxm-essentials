package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Periodically distributes play-time salary/paychecks to online players using the self-rescheduling loop pattern.
 * Amount is resolved dynamically using the Permissions port, falling back to a configured default.
 */
@NullMarked
public final class SalaryTask {

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final EconomyProvider economyProvider;
    private final Permissions permissions;
    private final EconomyNotifier notifier;
    private final Currency defaultCurrency;

    private final boolean enabled;
    private final Duration interval;
    private final long defaultAmount;
    private final QuotaFamily salaryQuotaFamily;
    private volatile boolean running;

    public SalaryTask(
            Plugin plugin,
            Scheduler scheduler,
            EconomyProvider economyProvider,
            Permissions permissions,
            EconomyNotifier notifier,
            Currency defaultCurrency,
            boolean enabled,
            Duration interval,
            long defaultAmount) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.economyProvider = Objects.requireNonNull(economyProvider, "economyProvider");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        this.enabled = enabled;
        this.interval = Objects.requireNonNull(interval, "interval");
        this.defaultAmount = defaultAmount;
        this.salaryQuotaFamily = QuotaFamily.quota("uxmessentials.economy.salary.amount");
    }

    public void start() {
        if (!enabled) {
            return;
        }
        running = true;
        scheduleNext();
    }

    public void stop() {
        running = false;
    }

    private void scheduleNext() {
        if (!running) {
            return;
        }
        scheduler.asyncAfter(interval, this::tick);
    }

    private void tick() {
        if (!running) {
            return;
        }
        // The online roster is global game state; enumerate it on the global region thread (Folia forbids the
        // off-thread getOnlinePlayers() this loop runs on), snapshot the refs, then pay each one off-tick.
        scheduler.onGlobal(() -> {
            if (!running) {
                return;
            }
            List<PlayerRef> recipients = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                recipients.add(new PlayerRef(player.getUniqueId(), player.getName()));
            }
            for (PlayerRef ref : recipients) {
                scheduler.async(() -> payOne(ref));
            }
            scheduleNext();
        });
    }

    private void payOne(PlayerRef ref) {
        long resolvedAmount = permissions
                .resolveQuota(ref, salaryQuotaFamily, null, defaultAmount)
                .orElse(defaultAmount);
        if (resolvedAmount <= 0) {
            return;
        }
        Money salary = Money.of(defaultCurrency, BigDecimal.valueOf(resolvedAmount));
        economyProvider.ensureAccount(ref, defaultCurrency);
        if (economyProvider.credit(ref, salary).isOk()) {
            notifier.send(ref, EconomyMessageKey.SALARY_PAYOUT, Map.of("amount", notifier.amount(salary)));
        }
    }
}
