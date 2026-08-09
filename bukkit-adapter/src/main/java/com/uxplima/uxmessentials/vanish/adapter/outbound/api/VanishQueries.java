package com.uxplima.uxmessentials.vanish.adapter.outbound.api;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.query.UxmVanishQuery;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.jspecify.annotations.NullMarked;

/**
 * The published vanish query, over the one vanish authority every other surface reads.
 *
 * <p>Nothing here waits on anything: the state is a small in-memory map, usually empty, and the see level a viewer
 * is measured by is a permission read.
 *
 * <p>{@link #canSee(UUID, UUID)} resolves the viewer's see level the same way the hide and reveal path does, so a
 * consumer filtering a list agrees with what the viewer actually has in front of them. Answering it from
 * {@link #isVanished(UUID)} alone would be wrong on any server that layers the vanish levels: a staff member is
 * meant to see the players below them.
 */
@NullMarked
public final class VanishQueries implements UxmVanishQuery {

    private static final int NOT_VANISHED = 0;

    private final VanishStore store;
    private final VanishLevelResolver levels;
    private final PlayerLookup players;

    public VanishQueries(VanishStore store, VanishLevelResolver levels, PlayerLookup players) {
        this.store = Objects.requireNonNull(store, "store");
        this.levels = Objects.requireNonNull(levels, "levels");
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public boolean isVanished(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return store.isVanished(playerId);
    }

    @Override
    public Set<UUID> vanished() {
        return Set.copyOf(store.vanished());
    }

    @Override
    public int levelOf(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return store.levelOf(playerId).map(VanishLevel::level).orElse(NOT_VANISHED);
    }

    @Override
    public boolean canSee(UUID viewerId, UUID targetId) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(targetId, "targetId");
        if (viewerId.equals(targetId)) {
            return true;
        }
        if (!store.isVanished(targetId)) {
            return true;
        }
        return store.snapshot().canSee(viewerId, targetId, levels.seeLevel(subject(viewerId)));
    }

    private PlayerRef subject(UUID playerId) {
        return ApiValues.subject(players, playerId);
    }
}
