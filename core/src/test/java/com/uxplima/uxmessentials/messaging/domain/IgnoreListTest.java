package com.uxplima.uxmessentials.messaging.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The {@link IgnoreList} aggregate invariants: the ignore-aware delivery rule per {@link IgnoreChannel}, the
 * scope semantics ({@code ALL} blocks every channel, the narrow scopes block one), and the idempotent
 * re-scope of a repeat ignore. The aggregate is pure and immutable between operations, so every rule is
 * asserted here in isolation.
 */
class IgnoreListTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef SENDER = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final PlayerRef OTHER = new PlayerRef(UUID.randomUUID(), "Carol");

    @Test
    void anEmptyListBlocksNothing() {
        IgnoreList list = IgnoreList.empty(OWNER);

        assertThat(list.blocks(SENDER, IgnoreChannel.MESSAGE)).isFalse();
        assertThat(list.blocks(SENDER, IgnoreChannel.MAIL)).isFalse();
    }

    @Test
    void anAllScopeIgnoreBlocksBothMessagesAndMail() {
        IgnoreList list = IgnoreList.empty(OWNER).ignore(SENDER, IgnoreScope.ALL);

        assertThat(list.blocks(SENDER, IgnoreChannel.MESSAGE)).isTrue();
        assertThat(list.blocks(SENDER, IgnoreChannel.MAIL)).isTrue();
    }

    @Test
    void aMessagesScopeBlocksMessagesButNotMail() {
        IgnoreList list = IgnoreList.empty(OWNER).ignore(SENDER, IgnoreScope.MESSAGES);

        assertThat(list.blocks(SENDER, IgnoreChannel.MESSAGE)).isTrue();
        assertThat(list.blocks(SENDER, IgnoreChannel.MAIL)).isFalse();
    }

    @Test
    void aMailScopeBlocksMailButNotMessages() {
        IgnoreList list = IgnoreList.empty(OWNER).ignore(SENDER, IgnoreScope.MAIL);

        assertThat(list.blocks(SENDER, IgnoreChannel.MAIL)).isTrue();
        assertThat(list.blocks(SENDER, IgnoreChannel.MESSAGE)).isFalse();
    }

    @Test
    void ignoringOnePlayerLeavesAnotherUnblocked() {
        IgnoreList list = IgnoreList.empty(OWNER).ignore(SENDER, IgnoreScope.ALL);

        assertThat(list.blocks(OTHER, IgnoreChannel.MESSAGE)).isFalse();
    }

    @Test
    void aRepeatIgnoreReScopesRatherThanDuplicating() {
        IgnoreList list =
                IgnoreList.empty(OWNER).ignore(SENDER, IgnoreScope.MESSAGES).ignore(SENDER, IgnoreScope.ALL);

        assertThat(list.size()).isEqualTo(1);
        assertThat(list.scopeFor(SENDER)).contains(IgnoreScope.ALL);
        assertThat(list.blocks(SENDER, IgnoreChannel.MAIL)).isTrue();
    }

    @Test
    void unignoreRemovesTheEntry() {
        IgnoreList list =
                IgnoreList.empty(OWNER).ignore(SENDER, IgnoreScope.ALL).unignore(SENDER);

        assertThat(list.ignores(SENDER)).isFalse();
        assertThat(list.blocks(SENDER, IgnoreChannel.MESSAGE)).isFalse();
    }

    @Test
    void unignoreOfAPlayerNotIgnoredIsANoOp() {
        IgnoreList list = IgnoreList.empty(OWNER).unignore(SENDER);

        assertThat(list.size()).isZero();
    }

    @Test
    void rebuildFromStoredEntriesPreservesScopes() {
        IgnoreList list = IgnoreList.of(
                OWNER, List.of(new IgnoreEntry(SENDER, IgnoreScope.MAIL), new IgnoreEntry(OTHER, IgnoreScope.ALL)));

        assertThat(list.scopeFor(SENDER)).contains(IgnoreScope.MAIL);
        assertThat(list.scopeFor(OTHER)).contains(IgnoreScope.ALL);
    }

    @Test
    void anUnknownStoredScopeDefaultsToAll() {
        assertThat(IgnoreScope.fromStored("SOMETHING_NEW")).isEqualTo(IgnoreScope.ALL);
        assertThat(IgnoreScope.fromStored(null)).isEqualTo(IgnoreScope.ALL);
    }
}
