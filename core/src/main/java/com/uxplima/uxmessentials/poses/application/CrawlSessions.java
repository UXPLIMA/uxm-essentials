package com.uxplima.uxmessentials.poses.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Tracks where each crawler's client-only fake block currently sits — the single source of truth for the block a
 * {@code CrawlView} must restore when the crawl moves or ends. A crawl, unlike a sit or a lie-down, is not anchored
 * on a seat entity: the player walks around and the fake block follows them, so the one piece of state a crawl
 * carries is the head-block position the fake block last occupied. {@link PoseSessions} still records that the
 * player is crawling; this registry records <em>where</em> the fake block is, so the restore always targets the
 * exact block and never leaves a phantom behind.
 *
 * <p>Backed by a {@code ConcurrentHashMap<UUID, Position>} keyed by the player's UUID: {@link #track} seeds the
 * head on {@code /crawl} and advances it on each block-crossing move (returning the position just vacated, which
 * the caller restores), and {@link #end} reads-and-removes it on any exit so the caller can restore that last
 * block. Cleared on module stop so a disable or reload leaves no residual crawl state.
 */
public final class CrawlSessions {

    private final ConcurrentHashMap<UUID, Position> heads = new ConcurrentHashMap<>();

    /**
     * The head block a standing player would occupy — a block directly above their feet — for the {@code feet}
     * position. This is the block the crawl fake-blocks so the client cannot stand; both the start and the
     * move-follow compute it the same way so the fake block always tracks the player's own column.
     */
    public static Position headBlockAbove(Position feet) {
        Objects.requireNonNull(feet, "feet");
        return Position.of(feet.world(), feet.blockX(), feet.blockY() + 1, feet.blockZ());
    }

    /**
     * Record {@code headBlock} as {@code who}'s current fake-block position, returning the position it replaced (the
     * block just vacated) or empty when they were not already tracked. The caller restores the returned block so the
     * fake block never lingers where the player no longer is.
     */
    public Optional<Position> track(PlayerRef who, Position headBlock) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(headBlock, "headBlock");
        return Optional.ofNullable(heads.put(who.uuid(), headBlock));
    }

    /** {@code who}'s current fake-block position, or empty when they are not crawling. */
    public Optional<Position> current(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return Optional.ofNullable(heads.get(who.uuid()));
    }

    /**
     * Stop tracking {@code who}, returning the last fake-block position so the caller can restore the real block
     * there, or empty when they were not crawling. Called on every crawl exit — the restore is what keeps a phantom
     * block off the player's screen.
     */
    public Optional<Position> end(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return Optional.ofNullable(heads.remove(who.uuid()));
    }

    /** How many players are crawling right now. */
    public int size() {
        return heads.size();
    }

    /** Drop every tracked crawl — called on module stop so a disable or reload leaves no residual state. */
    public void clear() {
        heads.clear();
    }
}
