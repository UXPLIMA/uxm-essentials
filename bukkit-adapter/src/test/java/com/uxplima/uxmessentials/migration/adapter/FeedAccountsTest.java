package com.uxplima.uxmessentials.migration.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A live balance import asks the economy about every player this server knows and every account that only ever played
 * on another server sharing the database, each once and under the name it plays under.
 */
class FeedAccountsTest {

    private ServerMock server;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void anAccountThatOnlyPlayedElsewhereIsAskedAboutUnderItsSharedNameAndALocalOneOnlyOnce() {
        PlayerMock here = server.addPlayer("Here");
        UUID elsewhere = UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6");
        FeedAccounts accounts = new FeedAccounts(
                server,
                () -> List.of(
                        new PlayerName(elsewhere, ".BedrockGamer", 1L),
                        new PlayerName(here.getUniqueId(), "Here", 1L)));

        assertThat(accounts.all())
                .extracting(account -> account.player().getUniqueId(), FeedAccounts.Account::name)
                .containsExactlyInAnyOrder(tuple(here.getUniqueId(), "Here"), tuple(elsewhere, ".BedrockGamer"));
    }
}
