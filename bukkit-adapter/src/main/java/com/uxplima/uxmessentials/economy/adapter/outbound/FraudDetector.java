package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * EconomyAudit decorator that implements algorithmic fraud detection.
 * Monitors single transaction spikes and velocity spikes (volume over time)
 * and dispatches alerts to a Discord webhook.
 */
@NullMarked
public final class FraudDetector implements EconomyAudit {

    private final EconomyAudit delegate;
    private final Logger log;

    private final boolean enabled;
    private final String webhookUrl;
    private final BigDecimal singleTransactionLimit;
    private final long velocityPeriodMs;
    private final BigDecimal velocityLimit;

    private final HttpClient httpClient;
    private final ConcurrentHashMap<UUID, List<TransactionRecord>> transactionHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastAlertTime = new ConcurrentHashMap<>();

    private record TransactionRecord(long timestamp, BigDecimal amount) {}

    public FraudDetector(
            EconomyAudit delegate,
            Logger log,
            boolean enabled,
            String webhookUrl,
            BigDecimal singleTransactionLimit,
            long velocityPeriodSeconds,
            BigDecimal velocityLimit) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.log = Objects.requireNonNull(log, "log");
        this.enabled = enabled;
        this.webhookUrl = Objects.requireNonNull(webhookUrl, "webhookUrl");
        this.singleTransactionLimit = Objects.requireNonNull(singleTransactionLimit, "singleTransactionLimit");
        this.velocityPeriodMs = velocityPeriodSeconds * 1000L;
        this.velocityLimit = Objects.requireNonNull(velocityLimit, "velocityLimit");
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Override
    public void credited(PlayerRef owner, Money amount, EconomyReason reason) {
        delegate.credited(owner, amount, reason);
        if (enabled) {
            checkSpike(owner, amount.amount(), "CREDIT", reason.name());
            checkVelocity(owner, amount.amount(), "CREDIT", reason.name());
        }
    }

    @Override
    public void debited(PlayerRef owner, Money amount, EconomyReason reason) {
        delegate.debited(owner, amount, reason);
        if (enabled) {
            checkSpike(owner, amount.amount(), "DEBIT", reason.name());
            checkVelocity(owner, amount.amount(), "DEBIT", reason.name());
        }
    }

    @Override
    public void rejected(PlayerRef owner, Money requested, EconomyReason reason) {
        delegate.rejected(owner, requested, reason);
    }

    @Override
    public void transferred(PlayerRef from, PlayerRef to, Money amount, EconomyReason reason) {
        delegate.transferred(from, to, amount, reason);
        if (enabled) {
            checkSpike(from, amount.amount(), "TRANSFER (to " + to.name() + ")", reason.name());
            checkVelocity(from, amount.amount(), "TRANSFER (to " + to.name() + ")", reason.name());
        }
    }

    @Override
    public void adminMutation(PlayerRef actor, PlayerRef target, Money amount, EconomyReason reason) {
        delegate.adminMutation(actor, target, amount, reason);
        if (enabled) {
            checkSpike(target, amount.amount(), "ADMIN MUTATION (by " + actor.name() + ")", reason.name());
        }
    }

    @Override
    public void bulkMutation(PlayerRef actor, Money amount, int affected, EconomyReason reason) {
        delegate.bulkMutation(actor, amount, affected, reason);
    }

    @Override
    public void worthSet(PlayerRef actor, String material, Money price) {
        delegate.worthSet(actor, material, price);
    }

    @Override
    public void worthCleared(PlayerRef actor, String material) {
        delegate.worthCleared(actor, material);
    }

    private void checkSpike(PlayerRef player, BigDecimal amount, String action, String reason) {
        if (amount.compareTo(singleTransactionLimit) >= 0) {
            String msg = "**Single Transaction Spike Alert**\n" + "**Player:** "
                    + player.name() + " (" + player.uuid() + ")\n" + "**Action:** "
                    + action + "\n" + "**Amount:** "
                    + amount.toPlainString() + "\n" + "**Reason:** "
                    + reason + "\n" + "**Time:** "
                    + Instant.now().toString();
            log.warn(
                    "[FRAUD ALERT] Single transaction spike detected for player {} ({}): {} amount={}",
                    player.name(),
                    player.uuid(),
                    action,
                    amount.toPlainString());
            sendAlert(msg);
        }
    }

    private void checkVelocity(PlayerRef player, BigDecimal amount, String action, String reason) {
        long now = System.currentTimeMillis();
        UUID uuid = player.uuid();

        transactionHistory.compute(uuid, (k, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(new TransactionRecord(now, amount));
            // Prune old history
            list.removeIf(rec -> now - rec.timestamp > velocityPeriodMs);

            // Calculate sum
            BigDecimal sum = BigDecimal.ZERO;
            for (TransactionRecord r : list) {
                sum = sum.add(r.amount());
            }

            if (sum.compareTo(velocityLimit) >= 0) {
                // Rate-limit alerts to once per minute per player
                long lastAlert = lastAlertTime.getOrDefault(uuid, 0L);
                if (now - lastAlert > 60000L) {
                    lastAlertTime.put(uuid, now);
                    String msg = "**High Transaction Velocity Alert**\n" + "**Player:** "
                            + player.name() + " (" + player.uuid() + ")\n" + "**Action:** "
                            + action + "\n" + "**Cumulative Volume:** "
                            + sum.toPlainString() + " (Limit: " + velocityLimit.toPlainString() + " over "
                            + (velocityPeriodMs / 1000L) + " seconds)\n" + "**Last Reason:** "
                            + reason + "\n" + "**Time:** "
                            + Instant.now().toString();

                    log.warn(
                            "[FRAUD ALERT] Velocity limit exceeded for player {} ({}): volume={} over last period",
                            player.name(),
                            player.uuid(),
                            sum.toPlainString());
                    sendAlert(msg);
                }
            }
            return list;
        });
    }

    private void sendAlert(String content) {
        if (webhookUrl.isBlank()) {
            return;
        }
        try {
            String json = "{\"embeds\":[{" + "\"title\":\"" + jsonEscape("uxmEssentials Economy Alert") + "\","
                    + "\"color\":16711680,"
                    + "\"description\":\""
                    + jsonEscape(content) + "\"," + "\"timestamp\":\""
                    + jsonEscape(Instant.now().toString()) + "\"" + "}]}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            var unused = httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("Failed to dispatch Discord webhook alert: " + e.getMessage());
        }
    }

    /**
     * Escape a string for embedding inside a JSON string literal. A player name carrying a quote, backslash, or
     * newline would otherwise break the webhook payload (or allow injection), so every control and meta character
     * is encoded per the JSON spec.
     */
    private static String jsonEscape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u");
                        String hex = Integer.toHexString(c);
                        out.append("0000", 0, 4 - hex.length()).append(hex);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
