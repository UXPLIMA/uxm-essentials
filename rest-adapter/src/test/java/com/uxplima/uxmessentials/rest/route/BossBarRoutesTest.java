package com.uxplima.uxmessentials.rest.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmActions;
import com.uxplima.uxmessentials.api.action.UxmBossBarActions;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmBossBarQuery;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import org.junit.jupiter.api.Test;

/** The boss bars over HTTP: what a player sees, hiding and showing them, and putting a bar up for a while. */
class BossBarRoutesTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String PLAYER_PATH = "/api/v1/players/" + PLAYER;

    @Test
    void aPlayersBarsComeBackWithTheirChoiceAndWhatIsOnScreen() {
        UxmBossBarQuery query = mock(UxmBossBarQuery.class);
        when(query.hidden(PLAYER)).thenReturn(CompletableFuture.completedFuture(Optional.of(false)));
        when(query.shown(PLAYER)).thenReturn(CompletableFuture.completedFuture(List.of("website", "store")));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.bossBar()).thenReturn(Optional.of(query));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/bossbar");

        assertThat(answer.status()).isEqualTo(HttpStatus.OK);
        assertThat(answer.object().get("hidden").getAsBoolean()).isFalse();
        assertThat(answer.object().getAsJsonArray("shown").toString()).isEqualTo("[\"website\",\"store\"]");
    }

    @Test
    void theWrittenBarsAreListed() {
        UxmBossBarQuery query = mock(UxmBossBarQuery.class);
        when(query.bars()).thenReturn(List.of("website", "drop"));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.bossBar()).thenReturn(Optional.of(query));

        Calls.Answer answer = Calls.get(api, "/api/v1/bossbars");

        assertThat(answer.object().getAsJsonArray("bars").toString()).isEqualTo("[\"website\",\"drop\"]");
    }

    @Test
    void postingToAPlayersBarsHidesThemUnlessTheBodySaysOtherwise() {
        UxmBossBarActions writes = mock(UxmBossBarActions.class);
        when(writes.hide(PLAYER)).thenReturn(done());
        when(writes.show(PLAYER)).thenReturn(done());

        assertThat(Calls.post(mock(UxmEssentialsApi.class), actions(writes), PLAYER_PATH + "/bossbar")
                        .ok())
                .isTrue();
        verify(writes).hide(PLAYER);

        Calls.post(mock(UxmEssentialsApi.class), actions(writes), PLAYER_PATH + "/bossbar", """
                {"hidden":false}""");
        verify(writes).show(PLAYER);
    }

    @Test
    void anAnnouncementAndASendCarryTheirSeconds() {
        UxmBossBarActions writes = mock(UxmBossBarActions.class);
        when(writes.announce("drop", Duration.ofMinutes(5))).thenReturn(done());
        when(writes.send(PLAYER, "drop", Duration.ofMinutes(1))).thenReturn(done());
        when(writes.refresh(PLAYER)).thenReturn(done());

        assertThat(Calls.post(mock(UxmEssentialsApi.class), actions(writes), "/api/v1/bossbars/drop/announce", """
                        {"seconds":300}""")
                        .ok())
                .isTrue();
        assertThat(Calls.post(mock(UxmEssentialsApi.class), actions(writes), PLAYER_PATH + "/bossbar/drop/send", """
                                {"seconds":60}""")
                        .ok())
                .isTrue();
        assertThat(Calls.post(mock(UxmEssentialsApi.class), actions(writes), PLAYER_PATH + "/bossbar/refresh")
                        .ok())
                .isTrue();
    }

    @Test
    void anAnnouncementWithNoLengthIsABadRequest() {
        UxmBossBarActions writes = mock(UxmBossBarActions.class);

        Calls.Answer answer =
                Calls.post(mock(UxmEssentialsApi.class), actions(writes), "/api/v1/bossbars/drop/announce");

        assertThat(answer.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(writes, never()).announce(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static CompletableFuture<UxmOutcome> done() {
        return CompletableFuture.completedFuture(UxmOutcome.ok());
    }

    private static UxmActions actions(UxmBossBarActions bossBar) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.bossBar()).thenReturn(Optional.of(bossBar));
        return actions;
    }
}
