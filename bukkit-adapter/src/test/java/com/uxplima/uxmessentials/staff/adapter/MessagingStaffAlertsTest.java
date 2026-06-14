package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.EchoMessages;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.NoopLogger;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.StaffKeySink;
import com.uxplima.uxmessentials.staff.adapter.outbound.MessagingStaffAlerts;
import com.uxplima.uxmessentials.staff.domain.event.StaffChatSent;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeEntered;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeExited;
import org.junit.jupiter.api.Test;

/**
 * The roster enter/exit alerts: {@link MessagingStaffAlerts} resolves the staff audience through the
 * {@link StaffAudience} node and fans the alert out to every holder EXCEPT the toggling player, who already
 * gets their own toggle feedback. The second half drives the same subscriber shape the wiring registers on the
 * in-process bus, proving an enter/exit event broadcasts while a {@code StaffChatSent} (and, by the same token,
 * the recovery path that publishes neither toggle event) stays silent.
 */
class MessagingStaffAlertsTest {

    private static final String NODE = "uxmessentials.staff.chat";

    @Test
    void announceEnterFansToEveryStaffMemberExceptTheActor() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
        PlayerRef carol = new PlayerRef(UUID.randomUUID(), "Carol");
        StaffKeySink sink = new StaffKeySink();
        MessagingStaffAlerts alerts =
                new MessagingStaffAlerts(fixedAudience(List.of(alice, bob, carol)), sink, new EchoMessages(), NODE);

        alerts.announceEnter(alice);

        // Bob and Carol hear it; Alice (the actor) does not — the echo folds the actor name into the enter key.
        assertThat(sink.delivered).hasSize(2);
        assertThat(sink.delivered).allMatch(line -> line.startsWith("staff.alert.enter") && line.contains("Alice"));
    }

    @Test
    void announceExitFansToEveryStaffMemberExceptTheActor() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
        StaffKeySink sink = new StaffKeySink();
        MessagingStaffAlerts alerts =
                new MessagingStaffAlerts(fixedAudience(List.of(alice, bob)), sink, new EchoMessages(), NODE);

        alerts.announceExit(bob);

        assertThat(sink.delivered).hasSize(1);
        assertThat(sink.delivered.get(0)).startsWith("staff.alert.exit").contains("Bob");
    }

    @Test
    void theSubscriberBroadcastsOnEnterAndExitButNotOnOtherStaffEvents() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
        StaffKeySink sink = new StaffKeySink();
        MessagingStaffAlerts alerts =
                new MessagingStaffAlerts(fixedAudience(List.of(alice, bob)), sink, new EchoMessages(), NODE);
        InProcessDomainEventPublisher events = new InProcessDomainEventPublisher(new NoopLogger());
        events.subscribe(alertSubscriber(alerts));

        events.publish(new StaffModeEntered(alice));
        events.publish(new StaffModeExited(alice));
        // A staff-chat event (and, like recovery, anything that is not an enter/exit toggle) must not alert.
        events.publish(new StaffChatSent(alice, "patrolling"));

        // Two broadcasts (enter + exit), each delivered to Bob only, never to the actor Alice.
        assertThat(sink.delivered).hasSize(2);
        assertThat(sink.delivered.get(0)).startsWith("staff.alert.enter");
        assertThat(sink.delivered.get(1)).startsWith("staff.alert.exit");
    }

    /** The exact subscriber shape StaffWiring registers on the bus, rebuilt here so the dispatch is testable. */
    private static Consumer<DomainEvent> alertSubscriber(MessagingStaffAlerts alerts) {
        return event -> {
            if (event instanceof StaffModeEntered entered) {
                alerts.announceEnter(entered.staff());
            } else if (event instanceof StaffModeExited exited) {
                alerts.announceExit(exited.staff());
            }
        };
    }

    private static StaffAudience fixedAudience(List<PlayerRef> audience) {
        return node -> NODE.equals(node) ? audience : List.of();
    }
}
