package com.uxplima.uxmessentials.discord;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.api.audit.AuditEntry;
import com.uxplima.uxmessentials.api.audit.AuditFeed;

/**
 * The host's {@link AuditFeed}, read as the {@link AuditNotice}s the bridge forwards.
 *
 * <p>An {@code economy_} line is an economy notice and carries its whole amount for {@code min-eco-notify}; every
 * other line is an audit notice. {@code actor} and {@code target} are lifted out of the fields, and the rest keep the
 * order the host wrote them in, so the line in Discord reads as the line in the log.
 */
final class HostAuditSource implements NotificationSource {

    private static final String ECONOMY_PREFIX = "economy_";
    private static final String SYSTEM = "system";
    private static final String LOCAL_ORIGIN = "local";

    private final AuditFeed feed;

    HostAuditSource(AuditFeed feed) {
        this.feed = Objects.requireNonNull(feed, "feed");
    }

    @Override
    public Subscription subscribe(Listener listener) {
        Objects.requireNonNull(listener, "listener");
        AuditFeed.Subscription open = feed.subscribe(entry -> listener.onNotice(notice(entry)));
        return open::close;
    }

    static AuditNotice notice(AuditEntry entry) {
        Map<String, String> fields = new LinkedHashMap<>(entry.fields());
        String actor = Optional.ofNullable(fields.remove("actor")).orElse(SYSTEM);
        Optional<String> target = Optional.ofNullable(fields.remove("target"));
        boolean economy = entry.event().startsWith(ECONOMY_PREFIX);
        return new AuditNotice(
                economy ? EventCategory.ECONOMY : EventCategory.AUDIT,
                entry.event(),
                actor,
                target,
                fields,
                economy ? entry.field("amount").flatMap(HostAuditSource::whole) : Optional.empty(),
                LOCAL_ORIGIN);
    }

    private static Optional<Long> whole(String amount) {
        try {
            return Optional.of(new BigDecimal(amount).longValue());
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
