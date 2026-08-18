package com.uxplima.uxmessentials.poses.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.poses.application.StartCrawl.CrawlOutcome;
import com.uxplima.uxmessentials.poses.application.port.CrawlView;
import com.uxplima.uxmessentials.poses.application.port.PoseRegionGate;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.poses.domain.event.PoseStarted;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link StartCrawl}: {@code /crawl} is denied when its feature switch is off, when the region gate refuses, or
 * when the player already poses; and a success holds the player prone at their own feet through the
 * {@link CrawlView}, records a {@link PoseType#CRAWL} session, and publishes {@link PoseStarted}. All ports are
 * fakes, so no Bukkit is involved.
 */
class StartCrawlTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final Position FEET = new Position(WORLD, 10.5, 64.0, 20.5, 90f, 0f);

    private final PoseSessions sessions = new PoseSessions();
    private final RecordingCrawlView crawlView = new RecordingCrawlView();
    private final RecordingEvents events = new RecordingEvents();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void deniesWhenTheCrawlFeatureIsDisabled() {
        StartCrawl off = newStartCrawl(gateAllowing(true), false);

        assertThat(off.start(WHO, FEET)).isEqualTo(CrawlOutcome.DISABLED);
        assertThat(crawlView.held).isEmpty();
        assertThat(sessions.isPosing(WHO)).isFalse();
        assertThat(events.published).isEmpty();
    }

    @Test
    void deniesWhenTheRegionGateRefuses() {
        StartCrawl startCrawl = newStartCrawl(gateAllowing(false), true);

        assertThat(startCrawl.start(WHO, FEET)).isEqualTo(CrawlOutcome.DENIED_REGION);
        assertThat(crawlView.held).isEmpty();
        assertThat(sessions.isPosing(WHO)).isFalse();
    }

    @Test
    void deniesWhenThePlayerIsAlreadyPosing() {
        sessions.start(new PoseSession(WHO, PoseType.SIT, FEET, "existing", null, clock.instant()));
        StartCrawl startCrawl = newStartCrawl(gateAllowing(true), true);

        assertThat(startCrawl.start(WHO, FEET)).isEqualTo(CrawlOutcome.ALREADY_POSING);
        assertThat(crawlView.held).isEmpty();
    }

    @Test
    void crawlingHoldsThePlayerAtTheirOwnFeetAndRecordsTheSession() {
        StartCrawl startCrawl = newStartCrawl(gateAllowing(true), true);

        assertThat(startCrawl.start(WHO, FEET)).isEqualTo(CrawlOutcome.STARTED);

        // The hold is stated at the player's own feet: the adapter, not the use case, decides how high the ceiling
        // that stops them standing back up sits above that.
        assertThat(crawlView.held).containsExactly(FEET);
        assertThat(crawlView.released).isEmpty();
        PoseSession session = sessions.current(WHO).orElseThrow();
        assertThat(session.type()).isEqualTo(PoseType.CRAWL);
        assertThat(events.published).singleElement().isInstanceOf(PoseStarted.class);
    }

    private StartCrawl newStartCrawl(PoseRegionGate gate, boolean crawlEnabled) {
        return new StartCrawl(sessions, crawlView, gate, events, clock, crawlEnabled, PoseCooldown.unlimited());
    }

    private static PoseRegionGate gateAllowing(boolean allow) {
        return (who, where, type) -> allow;
    }

    /** Records where the crawl view was asked to hold the player, and every release. */
    private static final class RecordingCrawlView implements CrawlView {
        private final List<Position> held = new ArrayList<>();
        private final List<PlayerRef> released = new ArrayList<>();

        @Override
        public void hold(PlayerRef who, Position feet) {
            held.add(feet);
        }

        @Override
        public void release(PlayerRef who) {
            released.add(who);
        }
    }

    /** Collects the events the use case publishes. */
    private static final class RecordingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }
}
