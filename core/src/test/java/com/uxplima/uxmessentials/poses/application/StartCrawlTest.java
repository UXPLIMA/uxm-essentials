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
import com.uxplima.uxmessentials.poses.application.port.PosePort;
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
 * when the player already poses; and a success shows a client-only fake block at the head (a block above the feet),
 * applies the swimming pose, records a {@link PoseType#CRAWL} session, tracks the fake-block position, and publishes
 * {@link PoseStarted}. All ports are fakes — no Bukkit.
 */
class StartCrawlTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final Position FEET = new Position(WORLD, 10.5, 64.0, 20.5, 90f, 0f);

    private final PoseSessions sessions = new PoseSessions();
    private final CrawlSessions crawlSessions = new CrawlSessions();
    private final RecordingCrawlView crawlView = new RecordingCrawlView();
    private final RecordingPosePort poses = new RecordingPosePort();
    private final RecordingEvents events = new RecordingEvents();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void deniesWhenTheCrawlFeatureIsDisabled() {
        StartCrawl off = newStartCrawl(gateAllowing(true), false);

        assertThat(off.start(WHO, FEET)).isEqualTo(CrawlOutcome.DISABLED);
        assertThat(crawlView.shown).isEmpty();
        assertThat(poses.applied).isEmpty();
        assertThat(sessions.isPosing(WHO)).isFalse();
        assertThat(crawlSessions.size()).isZero();
        assertThat(events.published).isEmpty();
    }

    @Test
    void deniesWhenTheRegionGateRefuses() {
        StartCrawl startCrawl = newStartCrawl(gateAllowing(false), true);

        assertThat(startCrawl.start(WHO, FEET)).isEqualTo(CrawlOutcome.DENIED_REGION);
        assertThat(crawlView.shown).isEmpty();
        assertThat(poses.applied).isEmpty();
        assertThat(sessions.isPosing(WHO)).isFalse();
    }

    @Test
    void deniesWhenThePlayerIsAlreadyPosing() {
        sessions.start(new PoseSession(WHO, PoseType.SIT, FEET, "existing", null, clock.instant()));
        StartCrawl startCrawl = newStartCrawl(gateAllowing(true), true);

        assertThat(startCrawl.start(WHO, FEET)).isEqualTo(CrawlOutcome.ALREADY_POSING);
        assertThat(crawlView.shown).isEmpty();
        assertThat(poses.applied).isEmpty();
    }

    @Test
    void crawlingShowsTheFakeBlockAppliesSwimmingAndRecordsTheSession() {
        StartCrawl startCrawl = newStartCrawl(gateAllowing(true), true);

        assertThat(startCrawl.start(WHO, FEET)).isEqualTo(CrawlOutcome.STARTED);

        // The fake block sits a block above the feet — the head block a standing player would occupy.
        Position head = CrawlSessions.headBlockAbove(FEET);
        assertThat(crawlView.shown).containsExactly(head);
        assertThat(crawlView.restored).isEmpty();
        assertThat(poses.applied).containsExactly(PoseType.CRAWL);
        PoseSession session = sessions.current(WHO).orElseThrow();
        assertThat(session.type()).isEqualTo(PoseType.CRAWL);
        // The crawl tracker knows exactly where the fake block is, so a later restore targets the right block.
        assertThat(crawlSessions.current(WHO)).contains(head);
        assertThat(events.published).singleElement().isInstanceOf(PoseStarted.class);
    }

    @Test
    void theHeadBlockIsOneBlockAboveTheFeetBlock() {
        // The suffocation-safe placement: the fake block is at the standing head cell (feet block Y + 1), above the
        // crawling body, so it never engulfs the ~0.6-block-tall prone hitbox.
        Position head = CrawlSessions.headBlockAbove(FEET);

        assertThat(head.blockX()).isEqualTo(FEET.blockX());
        assertThat(head.blockZ()).isEqualTo(FEET.blockZ());
        assertThat(head.blockY()).isEqualTo(FEET.blockY() + 1);
    }

    private StartCrawl newStartCrawl(PoseRegionGate gate, boolean crawlEnabled) {
        return new StartCrawl(
                sessions, crawlSessions, crawlView, poses, gate, events, clock, crawlEnabled, PoseCooldown.unlimited());
    }

    private static PoseRegionGate gateAllowing(boolean allow) {
        return (who, where, type) -> allow;
    }

    /** Records the head positions the crawl view was asked to fake and to restore. */
    private static final class RecordingCrawlView implements CrawlView {
        private final List<Position> shown = new ArrayList<>();
        private final List<Position> restored = new ArrayList<>();

        @Override
        public void showFakeBlockAbove(PlayerRef who, Position headBlock) {
            shown.add(headBlock);
        }

        @Override
        public void restoreRealBlock(PlayerRef who, Position headBlock) {
            restored.add(headBlock);
        }
    }

    /** Records which poses were rendered. */
    private static final class RecordingPosePort implements PosePort {
        private final List<PoseType> applied = new ArrayList<>();

        @Override
        public void applyPose(PlayerRef who, PoseType pose) {
            applied.add(pose);
        }

        @Override
        public void clearPose(PlayerRef who) {}
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
