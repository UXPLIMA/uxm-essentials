package com.uxplima.uxmessentials.discordlink.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class LinkStatusTest {

    private final InMemoryDiscordLinkStore store = new InMemoryDiscordLinkStore();
    private final LinkStatus status = new LinkStatus(store);
    private final PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");

    @Test
    void emptyWhenUnlinkedAndTheBindingWhenLinked() {
        assertThat(status.status(alice)).isEmpty();

        ConfirmedLink link = new ConfirmedLink(
                alice.uuid(), DiscordId.of("123456789012345678"), Instant.parse("2026-01-01T00:00:00Z"));
        store.confirm(link);

        assertThat(status.status(alice)).contains(link);
    }
}
