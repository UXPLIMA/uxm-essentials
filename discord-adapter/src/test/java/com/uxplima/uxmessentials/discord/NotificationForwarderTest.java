package com.uxplima.uxmessentials.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class NotificationForwarderTest {

    private static final String AUDIT_CHANNEL = "100";
    private static final String ECO_CHANNEL = "200";

    private DiscordConfig config(long minEco, Map<EventCategory, String> channels) {
        return new DiscordConfig(true, "token", channels, minEco, 60);
    }

    private Map<EventCategory, String> bothChannels() {
        Map<EventCategory, String> channels = new EnumMap<>(EventCategory.class);
        channels.put(EventCategory.AUDIT, AUDIT_CHANNEL);
        channels.put(EventCategory.ECONOMY, ECO_CHANNEL);
        return channels;
    }

    @Test
    void connectedAuditNotificationIsForwardedToMappedChannel() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        gateway.setConnected(true);
        NotificationForwarder forwarder = new NotificationForwarder(gateway, config(0, bothChannels()));

        boolean forwarded = forwarder.forward(Notification.audit("event=player_jail target=Steve"));

        assertThat(forwarded).isTrue();
        assertThat(gateway.sent())
                .containsExactly(new FakeDiscordGateway.Sent(AUDIT_CHANNEL, "event=player_jail target=Steve"));
    }

    @Test
    void nothingIsForwardedWhenTheGatewayIsNotConnected() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        gateway.setConnected(false);
        NotificationForwarder forwarder = new NotificationForwarder(gateway, config(0, bothChannels()));

        boolean forwarded = forwarder.forward(Notification.audit("anything"));

        assertThat(forwarded).isFalse();
        assertThat(gateway.sent()).isEmpty();
    }

    @Test
    void categoryWithNoMappedChannelIsDropped() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        gateway.setConnected(true);
        Map<EventCategory, String> auditOnly = new EnumMap<>(EventCategory.class);
        auditOnly.put(EventCategory.AUDIT, AUDIT_CHANNEL);
        NotificationForwarder forwarder = new NotificationForwarder(gateway, config(0, auditOnly));

        boolean forwarded = forwarder.forward(Notification.economy("event=eco_give amount=500", 500));

        assertThat(forwarded).isFalse();
        assertThat(gateway.sent()).isEmpty();
    }

    @Test
    void economyNotificationBelowThresholdIsDropped() {
        FakeDiscordGateway gateway = new FakeDiscordGateway();
        gateway.setConnected(true);
        NotificationForwarder forwarder = new NotificationForwarder(gateway, config(1000, bothChannels()));

        boolean below = forwarder.forward(Notification.economy("small pay", 999));
        boolean atThreshold = forwarder.forward(Notification.economy("big pay", 1000));

        assertThat(below).isFalse();
        assertThat(atThreshold).isTrue();
        assertThat(gateway.sent()).containsExactly(new FakeDiscordGateway.Sent(ECO_CHANNEL, "big pay"));
    }
}
