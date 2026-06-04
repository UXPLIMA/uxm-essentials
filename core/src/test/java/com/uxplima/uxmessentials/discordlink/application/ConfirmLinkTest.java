package com.uxplima.uxmessentials.discordlink.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.DiscordLinkError;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.Test;

class ConfirmLinkTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final LinkCode CODE = LinkCode.of("ABC234");
    private static final DiscordId DISCORD = DiscordId.of("123456789012345678");

    private final InMemoryDiscordLinkStore store = new InMemoryDiscordLinkStore();
    private final ConfirmLink confirm = new ConfirmLink(store, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void bindsThePlayerAndClearsThePendingRowOnSuccess() {
        store.savePending(new PendingLink(ALICE, CODE, NOW.plusSeconds(600)));

        Result<ConfirmedLink, DiscordLinkError> result = confirm.confirm(CODE, DISCORD);

        assertThat(result.isOk()).isTrue();
        ConfirmedLink link = result.orElseThrow();
        assertThat(link.player()).isEqualTo(ALICE);
        assertThat(link.discordId()).isEqualTo(DISCORD);
        assertThat(link.linkedAt()).isEqualTo(NOW);
        assertThat(store.findByPlayer(ALICE)).contains(link);
        assertThat(store.pendingOf(ALICE)).isEmpty(); // the code was consumed
    }

    @Test
    void rejectsAnUnknownCode() {
        Result<ConfirmedLink, DiscordLinkError> result = confirm.confirm(CODE, DISCORD);

        assertThat(result.errorOrThrow()).isEqualTo(DiscordLinkError.NO_PENDING_CODE);
    }

    @Test
    void rejectsAndClearsAnExpiredCode() {
        store.savePending(new PendingLink(ALICE, CODE, NOW)); // expiry == now is treated as expired

        Result<ConfirmedLink, DiscordLinkError> result = confirm.confirm(CODE, DISCORD);

        assertThat(result.errorOrThrow()).isEqualTo(DiscordLinkError.CODE_EXPIRED);
        assertThat(store.pendingOf(ALICE)).isEmpty(); // the stale row is swept
        assertThat(store.findByPlayer(ALICE)).isEmpty();
    }

    @Test
    void rejectsWhenTheDiscordAccountIsAlreadyBoundToADifferentPlayer() {
        store.confirm(new ConfirmedLink(BOB, DISCORD, NOW.minusSeconds(60)));
        store.savePending(new PendingLink(ALICE, CODE, NOW.plusSeconds(600)));

        Result<ConfirmedLink, DiscordLinkError> result = confirm.confirm(CODE, DISCORD);

        assertThat(result.errorOrThrow()).isEqualTo(DiscordLinkError.DISCORD_ALREADY_LINKED);
        assertThat(store.findByPlayer(ALICE)).isEmpty(); // Alice is not bound
        assertThat(store.findByDiscordId(DISCORD).orElseThrow().player()).isEqualTo(BOB); // Bob keeps the binding
    }

    @Test
    void reConfirmingForTheSamePlayerIsIdempotent() {
        store.confirm(new ConfirmedLink(ALICE, DISCORD, NOW.minusSeconds(60)));
        store.savePending(new PendingLink(ALICE, CODE, NOW.plusSeconds(600)));

        Result<ConfirmedLink, DiscordLinkError> result = confirm.confirm(CODE, DISCORD);

        assertThat(result.isOk()).isTrue();
        assertThat(result.orElseThrow().player()).isEqualTo(ALICE);
        assertThat(store.findByDiscordId(DISCORD).orElseThrow().player()).isEqualTo(ALICE);
    }
}
