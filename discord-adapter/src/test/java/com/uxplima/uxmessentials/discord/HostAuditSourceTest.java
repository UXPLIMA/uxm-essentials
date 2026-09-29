package com.uxplima.uxmessentials.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.api.audit.AuditEntry;
import com.uxplima.uxmessentials.api.audit.AuditFeed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The host's audit feed, read as the notices the bridge forwards.
 *
 * <p>The bridge used to look up a source only it declared, which no host could register, so nothing it was built to
 * forward ever reached Discord.
 */
class HostAuditSourceTest {

    private final FakeFeed feed = new FakeFeed();
    private final List<AuditNotice> heard = new ArrayList<>();

    @Test
    @DisplayName("a moderation line becomes an audit notice with its actor, its target and the rest in order")
    void aModerationLineIsAnAuditNotice() {
        new HostAuditSource(feed).subscribe(heard::add);

        feed.emit(entry("player_mute", "actor", "a-uuid", "target", "t-uuid", "ok", "true", "reason", "\"spam\""));

        assertThat(heard).singleElement().satisfies(notice -> {
            assertThat(notice.category()).isEqualTo(EventCategory.AUDIT);
            assertThat(notice.event()).isEqualTo("player_mute");
            assertThat(notice.actor()).isEqualTo("a-uuid");
            assertThat(notice.target()).contains("t-uuid");
            assertThat(notice.fields()).containsExactly(Map.entry("ok", "true"), Map.entry("reason", "\"spam\""));
            assertThat(notice.amount()).isEmpty();
            assertThat(notice.fromBridge()).isFalse();
        });
    }

    @Test
    @DisplayName("an economy line is an economy notice carrying its whole amount")
    void anEconomyLineCarriesItsAmount() {
        new HostAuditSource(feed).subscribe(heard::add);

        feed.emit(entry("economy_admin", "actor", "a", "target", "t", "currency", "coins", "amount", "1250.75"));
        feed.emit(entry("economy_setworth_clear", "actor", "a", "material", "DIAMOND"));

        assertThat(heard).extracting(AuditNotice::category).containsOnly(EventCategory.ECONOMY);
        assertThat(heard).extracting(AuditNotice::amount).containsExactly(Optional.of(1250L), Optional.empty());
    }

    @Test
    @DisplayName("a line with no actor is the system's, and an amount that is not a number is no amount")
    void missingFieldsHaveTheirDefaults() {
        new HostAuditSource(feed).subscribe(heard::add);

        feed.emit(entry("economy_transfer", "amount", "lots"));

        assertThat(heard).singleElement().satisfies(notice -> {
            assertThat(notice.actor()).isEqualTo("system");
            assertThat(notice.target()).isEmpty();
            assertThat(notice.amount()).isEmpty();
        });
    }

    @Test
    @DisplayName("closing the bridge's subscription closes the one it holds on the host")
    void closingReachesTheHost() {
        NotificationSource.Subscription open = new HostAuditSource(feed).subscribe(heard::add);
        open.close();

        feed.emit(entry("player_kick", "actor", "a"));

        assertThat(heard).isEmpty();
    }

    private static AuditEntry entry(String event, String... pairs) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            fields.put(pairs[i], pairs[i + 1]);
        }
        return new AuditEntry(event, fields);
    }

    private static final class FakeFeed implements AuditFeed {
        private final List<Consumer<AuditEntry>> listeners = new ArrayList<>();

        @Override
        public Subscription subscribe(Consumer<AuditEntry> listener) {
            listeners.add(listener);
            return () -> listeners.remove(listener);
        }

        void emit(AuditEntry entry) {
            for (Consumer<AuditEntry> listener : List.copyOf(listeners)) {
                listener.accept(entry);
            }
        }
    }
}
