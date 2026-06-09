package com.uxplima.uxmessentials.economy.adapter.inbound.listener;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.uxplima.uxmessentials.economy.adapter.EconomyConfig;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * Listens to {@link PlayerCommandPreprocessEvent} and charges players a configured fee to run commands.
 */
@NullMarked
public final class CommandCostListener implements Listener {

    private static final String BYPASS_PERMISSION = "uxmessentials.economy.bypasscmdcost";

    private final EconomyProvider economy;
    private final Map<String, EconomyConfig.CommandCost> commandCosts;
    private final Currency defaultCurrency;
    private final EconomyNotifier notifier;

    public CommandCostListener(
            EconomyProvider economy,
            Map<String, EconomyConfig.CommandCost> commandCosts,
            Currency defaultCurrency,
            EconomyNotifier notifier) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.commandCosts = Map.copyOf(commandCosts);
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (commandCosts.isEmpty()) {
            return;
        }

        String label = label(event.getMessage());
        EconomyConfig.CommandCost cost = commandCosts.get(label);
        if (cost == null || cost.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }

        PlayerRef owner = BukkitRefs.toRef(player);
        Currency targetCurrency = economy.currencies().stream()
                .filter(c -> c.id().value().equalsIgnoreCase(cost.currencyId()))
                .findFirst()
                .orElse(defaultCurrency);
        Money chargeAmount = Money.of(targetCurrency, cost.amount());

        // Deduct money from player balance
        Result<Unit, ?> result = economy.debit(owner, chargeAmount);
        if (result.isOk()) {
            // Charged successfully
            notifier.send(
                    owner,
                    EconomyMessageKey.COMMAND_COST_CHARGED,
                    Map.of("amount", notifier.amount(chargeAmount), "command", "/" + label));
        } else {
            // Insufficient funds
            event.setCancelled(true);
            notifier.send(
                    owner,
                    EconomyMessageKey.COMMAND_COST_INSUFFICIENT,
                    Map.of("amount", notifier.amount(chargeAmount), "command", "/" + label));
        }
    }

    private static String label(String rawMessage) {
        String body = rawMessage.startsWith("/") ? rawMessage.substring(1) : rawMessage;
        int space = body.indexOf(' ');
        String label = space < 0 ? body : body.substring(0, space);
        return label.toLowerCase(Locale.ROOT);
    }
}
