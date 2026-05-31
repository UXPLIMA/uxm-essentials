package com.uxplima.uxmessentials.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class AuditNoticeSubscriberTest {

    private static final String AUDIT_CHANNEL = "100";
    private static final String ECO_CHANNEL = "200";

    /** In-memory host feed: hand it to the subscriber, then push notices through the registered listener. */
    private static final class FakeNotificationSource implements NotificationSource {
        private final List<Listener> listeners = new ArrayList<>();
        private int closes;

        @Override
        public Subscription subscribe(Listener listener) {
            listeners.add(listener);
            return () -> closes++;
        }

        void emit(AuditNotice notice) {
            for (Listener listener : List.copyOf(listeners)) {
                listener.onNotice(notice);
            }
        }

        int activeListeners() {
            return listeners.size() - closes;
        }
    }

    private DiscordConfig config(long minEco, int maxPerMinute, Map<EventCategory, String> channels) {
        return new DiscordConfig(true, "token", channels, minEco, maxPerMinute);
    }

    private Map<EventCategory, String> bothChannels() {
        Map<EventCategory, String> channels = new EnumMap<>(EventCategory.class);
        channels.put(EventCategory.AUDIT, AUDIT_CHANNEL);
        channels.put(EventCategory.ECONOMY, ECO_CHANNEL);
        return channels;
    }

    private NotificationRateLimiter limiter(int maxPerMinute, AtomicLong clock) {
        return new NotificationRateLimiter(maxPerMinute, Duration.ofMinutes(1), clock::get);
    }

    private AuditNotice auditNotice(String origin) {
        return new AuditNotice(
                EventCategory.AUDIT, "player_jail", "Admin", Optional.of("Steve"), Map.of(), Optional.empty(), origin);
    }

    @Test
    void auditEventInForwardsFormattedMessageToTheMappedChannel() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        gateway.setConnected(true);
        FakeNotificationSource source = new FakeNotificationSource();
        NotificationForwarder forwarder = new NotificationForwarder(gateway, config(0, 60, bothChannels()));
        AuditNoticeSubscriber subscriber = new AuditNoticeSubscriber(source, forwarder, limiter(60, new AtomicLong()));
        subscriber.start();

        source.emit(auditNotice("survival-1"));

        assertThat(gateway.sent())
                .containsExactly(
                        new FakeDiscordGateway.Sent(AUDIT_CHANNEL, "event=player_jail actor=Admin target=Steve"));
    }

    @Test
    void economyEventInForwardsToTheEconomyChannel() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        gateway.setConnected(true);
        FakeNotificationSource source = new FakeNotificationSource();
        NotificationForwarder forwarder = new NotificationForwarder(gateway, config(0, 60, bothChannels()));
        AuditNoticeSubscriber subscriber = new AuditNoticeSubscriber(source, forwarder, limiter(60, new AtomicLong()));
        subscriber.start();

        source.emit(new AuditNotice(
                EventCategory.ECONOMY,
                "eco_give",
                "Admin",
                Optional.of("Alex"),
                Map.of("amount", "750"),
                Optional.of(750L),
                "survival-1"));

        assertThat(gateway.sent())
                .containsExactly(
                        new FakeDiscordGateway.Sent(ECO_CHANNEL, "event=eco_give actor=Admin target=Alex amount=750"));
    }

    @Test
    void loopSentinelDropsADiscordOriginatedEvent() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        gateway.setConnected(true);
        FakeNotificationSource source = new FakeNotificationSource();
        NotificationForwarder forwarder = new NotificationForwarder(gateway, config(0, 60, bothChannels()));
        AuditNoticeSubscriber subscriber = new AuditNoticeSubscriber(source, forwarder, limiter(60, new AtomicLong()));
        subscriber.start();

        source.emit(auditNotice(AuditNotice.DISCORD_ORIGIN));

        assertThat(gateway.sent()).isEmpty();
    }

    @Test
    void disabledCategoryWithNoMappedChannelForwardsNothing() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        gateway.setConnected(true);
        FakeNotificationSource source = new FakeNotificationSource();
        Map<EventCategory, String> auditOnly = new EnumMap<>(EventCategory.class);
        auditOnly.put(EventCategory.AUDIT, AUDIT_CHANNEL);
        NotificationForwarder forwarder = new NotificationForwarder(gateway, config(0, 60, auditOnly));
        AuditNoticeSubscriber subscriber = new AuditNoticeSubscriber(source, forwarder, limiter(60, new AtomicLong()));
        subscriber.start();

        source.emit(new AuditNotice(
                EventCategory.ECONOMY,
                "eco_give",
                "Admin",
                Optional.of("Alex"),
                Map.of("amount", "750"),
                Optional.of(750L),
                "survival-1"));

        assertThat(gateway.sent()).isEmpty();
    }

    @Test
    void floodBeyondTheWindowBudgetIsRateLimited() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        gateway.setConnected(true);
        FakeNotificationSource source = new FakeNotificationSource();
        NotificationForwarder forwarder = new NotificationForwarder(gateway, config(0, 2, bothChannels()));
        AtomicLong clock = new AtomicLong(0L);
        AuditNoticeSubscriber subscriber = new AuditNoticeSubscriber(source, forwarder, limiter(2, clock));
        subscriber.start();

        for (int i = 0; i < 5; i++) {
            source.emit(auditNotice("survival-1"));
        }

        // Budget is 2 per minute; the same window swallows the other three.
        assertThat(gateway.sent()).hasSize(2);

        // Roll the window forward and the budget is replenished.
        clock.set(Duration.ofMinutes(1).toMillis());
        source.emit(auditNotice("survival-1"));
        assertThat(gateway.sent()).hasSize(3);
    }

    @Test
    void droppedLoopNoticeDoesNotConsumeFloodBudget() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        gateway.setConnected(true);
        FakeNotificationSource source = new FakeNotificationSource();
        NotificationForwarder forwarder = new NotificationForwarder(gateway, config(0, 1, bothChannels()));
        AuditNoticeSubscriber subscriber = new AuditNoticeSubscriber(source, forwarder, limiter(1, new AtomicLong()));
        subscriber.start();

        source.emit(auditNotice(AuditNotice.DISCORD_ORIGIN)); // dropped before the limiter
        source.emit(auditNotice("survival-1")); // still has the full budget

        assertThat(gateway.sent()).hasSize(1);
    }

    @Test
    void stopClosesTheSubscriptionAndIsIdempotent() {
        FakeNotificationSource source = new FakeNotificationSource();
        NotificationForwarder forwarder =
                new NotificationForwarder(new FakeDiscordGateway(), config(0, 60, bothChannels()));
        AuditNoticeSubscriber subscriber = new AuditNoticeSubscriber(source, forwarder, limiter(60, new AtomicLong()));

        subscriber.start();
        assertThat(source.activeListeners()).isEqualTo(1);

        subscriber.stop();
        subscriber.stop();
        assertThat(source.activeListeners()).isZero();
    }
}
