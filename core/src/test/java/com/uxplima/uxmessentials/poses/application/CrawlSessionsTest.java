package com.uxplima.uxmessentials.poses.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link CrawlSessions}: it tracks each crawler's current fake-block head position, hands back the position it
 * replaces so a move can restore the block just vacated, reads-and-removes on end so an exit can restore the last
 * block, and computes the head block one cell above the feet.
 */
class CrawlSessionsTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final Position HEAD_A = Position.of(WORLD, 3, 65, 7);
    private static final Position HEAD_B = Position.of(WORLD, 4, 65, 7);

    private final CrawlSessions crawlSessions = new CrawlSessions();

    @Test
    void headBlockAboveIsOneCellAboveTheFeet() {
        Position feet = new Position(WORLD, 3.4, 64.0, 7.9, 12f, 0f);

        Position head = CrawlSessions.headBlockAbove(feet);

        assertThat(head.blockX()).isEqualTo(3);
        assertThat(head.blockY()).isEqualTo(65);
        assertThat(head.blockZ()).isEqualTo(7);
        assertThat(head.world()).isEqualTo(WORLD);
    }

    @Test
    void trackReturnsEmptyForTheFirstHeadThenThePreviousOne() {
        assertThat(crawlSessions.track(WHO, HEAD_A)).isEmpty();
        assertThat(crawlSessions.current(WHO)).contains(HEAD_A);

        // Advancing to a new head returns the block just vacated, which the move-follow restores.
        assertThat(crawlSessions.track(WHO, HEAD_B)).contains(HEAD_A);
        assertThat(crawlSessions.current(WHO)).contains(HEAD_B);
        assertThat(crawlSessions.size()).isEqualTo(1);
    }

    @Test
    void endReadsAndRemovesTheLastHead() {
        crawlSessions.track(WHO, HEAD_A);

        assertThat(crawlSessions.end(WHO)).contains(HEAD_A);
        assertThat(crawlSessions.current(WHO)).isEmpty();
        assertThat(crawlSessions.end(WHO)).isEmpty();
        assertThat(crawlSessions.size()).isZero();
    }

    @Test
    void clearDropsEveryTrackedCrawl() {
        crawlSessions.track(WHO, HEAD_A);
        crawlSessions.track(new PlayerRef(UUID.randomUUID(), "Alex"), HEAD_B);
        assertThat(crawlSessions.size()).isEqualTo(2);

        crawlSessions.clear();

        assertThat(crawlSessions.size()).isZero();
    }
}
