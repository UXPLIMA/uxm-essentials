package com.uxplima.uxmessentials.discordlink.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.DiscordLinkError;
import com.uxplima.uxmessentials.discordlink.domain.event.AccountUnlinked;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class UnlinkTest {

    private final InMemoryDiscordLinkStore store = new InMemoryDiscordLinkStore();
    private final List<DomainEvent> published = new ArrayList<>();
    private final Unlink unlink = new Unlink(store, published::add);
    private final PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");

    @Test
    void removesAnExistingBindingThenReportsNotLinked() {
        store.confirm(new ConfirmedLink(
                alice.uuid(), DiscordId.of("123456789012345678"), Instant.parse("2026-01-01T00:00:00Z")));

        assertThat(unlink.unlink(alice).isOk()).isTrue();
        assertThat(store.findByPlayer(alice.uuid())).isEmpty();
        assertThat(unlink.unlink(alice).errorOrThrow()).isEqualTo(DiscordLinkError.NOT_LINKED);
    }

    @Test
    void announcesTheRemovedBindingOnceAndOnlyWhenOneWasRemoved() {
        store.confirm(new ConfirmedLink(
                alice.uuid(), DiscordId.of("123456789012345678"), Instant.parse("2026-01-01T00:00:00Z")));

        unlink.unlink(alice);
        unlink.unlink(alice); // the second call removes nothing, so it announces nothing

        assertThat(published).containsExactly(new AccountUnlinked(alice, DiscordId.of("123456789012345678")));
    }
}
