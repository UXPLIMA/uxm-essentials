package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.shared.application.port.StoredPermissions.Answer;
import org.junit.jupiter.api.Test;

/**
 * LuckPerms makes up an empty user for an account its storage never saw, so a player it has no name for cannot be
 * read at all; one it knows is read for the node.
 */
class LuckPermsNodeStoreTest {

    private static final String NODE = ModerationGuard.EXEMPT_NODE;
    private static final UUID KNOWN_EXEMPT = new UUID(0L, 1L);
    private static final UUID KNOWN = new UUID(0L, 2L);
    private static final UUID NEVER_SEEN = new UUID(0L, 3L);

    private final LuckPermsNodeStore store = new LuckPermsNodeStore(new Storage());

    @Test
    void aPlayerTheStorageNeverSawCannotBeRead() {
        assertThat(store.prepare(NEVER_SEEN, NODE).run()).isCompletedWithValue(Answer.NO_SOURCE);
    }

    @Test
    void aPlayerTheStorageKnowsIsReadForTheNode() {
        assertThat(store.prepare(KNOWN_EXEMPT, NODE).run()).isCompletedWithValue(Answer.HELD);
        assertThat(store.prepare(KNOWN, NODE).run()).isCompletedWithValue(Answer.NOT_HELD);
    }

    /** A LuckPerms storage that has seen two players, one of them granted the node. */
    private static final class Storage implements LuckPermsNodeStore.Users {

        @Override
        public CompletableFuture<Optional<String>> knownName(UUID player) {
            return CompletableFuture.completedFuture(
                    Set.of(KNOWN_EXEMPT, KNOWN).contains(player) ? Optional.of("Known") : Optional.empty());
        }

        @Override
        public CompletableFuture<Boolean> grants(UUID player, String node) {
            if (player.equals(NEVER_SEEN)) {
                throw new AssertionError("an unknown player is never loaded: LuckPerms would make one up");
            }
            return CompletableFuture.completedFuture(player.equals(KNOWN_EXEMPT) && node.equals(NODE));
        }
    }
}
