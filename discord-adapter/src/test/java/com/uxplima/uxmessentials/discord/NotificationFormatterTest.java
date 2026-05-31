package com.uxplima.uxmessentials.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class NotificationFormatterTest {

    private static Map<String, String> fields(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void auditNoticeFormatsAsGreppableLineForTheAuditCategory() {
        AuditNotice notice = new AuditNotice(
                EventCategory.AUDIT,
                "player_jail",
                "Admin",
                Optional.of("Steve"),
                fields("duration", "1h", "ok", "true"),
                Optional.empty(),
                "survival-1");

        Optional<Notification> result = NotificationFormatter.format(notice);

        assertThat(result).isPresent();
        Notification notification = result.orElseThrow();
        assertThat(notification.category()).isEqualTo(EventCategory.AUDIT);
        assertThat(notification.message()).isEqualTo("event=player_jail actor=Admin target=Steve duration=1h ok=true");
    }

    @Test
    void economyNoticeCarriesItsAmountForThresholdFiltering() {
        AuditNotice notice = new AuditNotice(
                EventCategory.ECONOMY,
                "eco_set",
                "Admin",
                Optional.of("Alex"),
                fields("currency", "coins", "amount", "5000"),
                Optional.of(5000L),
                "survival-1");

        Optional<Notification> result = NotificationFormatter.format(notice);

        assertThat(result).isPresent();
        Notification notification = result.orElseThrow();
        assertThat(notification.category()).isEqualTo(EventCategory.ECONOMY);
        assertThat(notification.amount()).contains(5000L);
        assertThat(notification.message())
                .isEqualTo("event=eco_set actor=Admin target=Alex currency=coins amount=5000");
    }

    @Test
    void noticeWithoutTargetOmitsTheTargetField() {
        AuditNotice notice = new AuditNotice(
                EventCategory.AUDIT,
                "reload_all",
                "console",
                Optional.empty(),
                fields("all_ok", "true"),
                Optional.empty(),
                "survival-1");

        Optional<Notification> result = NotificationFormatter.format(notice);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().message()).isEqualTo("event=reload_all actor=console all_ok=true");
    }

    @Test
    void bridgeOriginatedNoticeIsDroppedByTheLoopSentinel() {
        AuditNotice looped = new AuditNotice(
                EventCategory.AUDIT,
                "player_jail",
                "Admin",
                Optional.of("Steve"),
                Map.of(),
                Optional.empty(),
                AuditNotice.DISCORD_ORIGIN);

        assertThat(NotificationFormatter.format(looped)).isEmpty();
    }
}
