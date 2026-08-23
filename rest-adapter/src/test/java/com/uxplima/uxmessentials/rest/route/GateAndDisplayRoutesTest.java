package com.uxplima.uxmessentials.rest.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmActions;
import com.uxplima.uxmessentials.api.action.UxmNametagActions;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmScoreboardActions;
import com.uxplima.uxmessentials.api.action.UxmTablistActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.query.UxmCommandControlQuery;
import com.uxplima.uxmessentials.api.query.UxmScoreboardQuery;
import com.uxplima.uxmessentials.api.view.UxmCommandCheck;
import com.uxplima.uxmessentials.api.view.UxmCommandRule;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import org.junit.jupiter.api.Test;

/**
 * The command gate and the three display surfaces over HTTP.
 *
 * <p>What is pinned here is what each route does that a golden route table cannot say: the command to check is
 * required rather than defaulted, an answer that depends on a live player is a {@code 404} when there is none, and
 * a sidebar preference nobody can read comes back as null rather than as a guess.
 */
class GateAndDisplayRoutesTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String PLAYER_PATH = "/api/v1/players/" + PLAYER;

    @Test
    void aCheckWithNoCommandToCheckIsABadRequest() {
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.commandControl()).thenReturn(Optional.of(mock(UxmCommandControlQuery.class)));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/command-check");

        assertThat(answer.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(answer.message()).contains("command");
    }

    @Test
    void aBlockedCommandComesBackWithTheRuleThatBlockedIt() {
        UxmCommandControlQuery gate = mock(UxmCommandControlQuery.class);
        when(gate.check(PLAYER, "fly"))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(new UxmCommandCheck(
                        "fly", false, UxmCommandRule.BLACKLISTED, Optional.of("default"), Optional.of("world")))));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.commandControl()).thenReturn(Optional.of(gate));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/command-check", Map.of("command", "fly"));

        assertThat(answer.status()).isEqualTo(HttpStatus.OK);
        assertThat(answer.object().get("command").getAsString()).isEqualTo("fly");
        assertThat(answer.object().get("allowed").getAsBoolean()).isFalse();
        assertThat(answer.object().get("rule").getAsString()).isEqualTo("BLACKLISTED");
        assertThat(answer.object().get("group").getAsString()).isEqualTo("default");
        assertThat(answer.object().get("world").getAsString()).isEqualTo("world");
    }

    @Test
    void aCheckForSomebodyWhoIsNotHereIsANotFound() {
        UxmCommandControlQuery gate = mock(UxmCommandControlQuery.class);
        when(gate.check(any(), any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.commandControl()).thenReturn(Optional.of(gate));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/command-check", Map.of("command", "fly"));

        assertThat(answer.status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aSidebarPreferenceNobodyCanReadIsNullRatherThanAGuess() {
        UxmScoreboardQuery scoreboard = mock(UxmScoreboardQuery.class);
        when(scoreboard.hidden(PLAYER)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(scoreboard.activeBoard(PLAYER)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(scoreboard.yielded(PLAYER)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.scoreboard()).thenReturn(Optional.of(scoreboard));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/scoreboard");

        assertThat(answer.object().get("hidden").isJsonNull()).isTrue();
        assertThat(answer.object().get("active-board").isJsonNull()).isTrue();
        assertThat(answer.object().get("yielded").isJsonNull()).isTrue();
    }

    @Test
    void aLiveSidebarReadCarriesItsSelectedBoardAndOwnershipState() {
        UxmScoreboardQuery scoreboard = mock(UxmScoreboardQuery.class);
        when(scoreboard.hidden(PLAYER)).thenReturn(CompletableFuture.completedFuture(Optional.of(false)));
        when(scoreboard.activeBoard(PLAYER)).thenReturn(CompletableFuture.completedFuture(Optional.of("lobby")));
        when(scoreboard.yielded(PLAYER)).thenReturn(CompletableFuture.completedFuture(Optional.of(false)));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);
        when(api.scoreboard()).thenReturn(Optional.of(scoreboard));

        Calls.Answer answer = Calls.get(api, PLAYER_PATH + "/scoreboard");

        assertThat(answer.object().get("hidden").getAsBoolean()).isFalse();
        assertThat(answer.object().get("active-board").getAsString()).isEqualTo("lobby");
        assertThat(answer.object().get("yielded").getAsBoolean()).isFalse();
    }

    @Test
    void postingToTheSidebarHidesItUnlessTheBodySaysOtherwise() {
        UxmScoreboardActions writes = mock(UxmScoreboardActions.class);
        when(writes.hide(PLAYER)).thenReturn(done());
        when(writes.show(PLAYER)).thenReturn(done());

        assertThat(Calls.post(mock(UxmEssentialsApi.class), scoreboard(writes), PLAYER_PATH + "/scoreboard")
                        .ok())
                .isTrue();
        verify(writes).hide(PLAYER);

        Calls.post(mock(UxmEssentialsApi.class), scoreboard(writes), PLAYER_PATH + "/scoreboard", """
                {"hidden":false}""");
        verify(writes).show(PLAYER);
    }

    @Test
    void eachDisplayRefreshReachesItsOwnModule() {
        UxmScoreboardActions sidebar = mock(UxmScoreboardActions.class);
        when(sidebar.refresh(PLAYER)).thenReturn(done());
        UxmTablistActions tablist = mock(UxmTablistActions.class);
        when(tablist.refresh(PLAYER)).thenReturn(done());
        UxmNametagActions nametags = mock(UxmNametagActions.class);
        when(nametags.refresh(PLAYER)).thenReturn(done());

        UxmActions actions = mock(UxmActions.class);
        when(actions.scoreboard()).thenReturn(Optional.of(sidebar));
        when(actions.tablist()).thenReturn(Optional.of(tablist));
        when(actions.nametags()).thenReturn(Optional.of(nametags));
        UxmEssentialsApi api = mock(UxmEssentialsApi.class);

        assertThat(Calls.post(api, actions, PLAYER_PATH + "/scoreboard/refresh").ok())
                .isTrue();
        assertThat(Calls.post(api, actions, PLAYER_PATH + "/tablist/refresh").ok())
                .isTrue();
        assertThat(Calls.post(api, actions, PLAYER_PATH + "/nametag/refresh").ok())
                .isTrue();

        verify(sidebar).refresh(PLAYER);
        verify(tablist).refresh(PLAYER);
        verify(nametags).refresh(PLAYER);
    }

    @Test
    void aDisplayModuleThatIsOffIsAServiceUnavailable() {
        UxmActions actions = mock(UxmActions.class);
        when(actions.tablist()).thenReturn(Optional.empty());

        Calls.Answer answer = Calls.post(mock(UxmEssentialsApi.class), actions, PLAYER_PATH + "/tablist/refresh");

        assertThat(answer.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(answer.message()).contains("tablist");
    }

    private static CompletableFuture<UxmOutcome> done() {
        return CompletableFuture.completedFuture(UxmOutcome.ok());
    }

    private static UxmActions scoreboard(UxmScoreboardActions scoreboard) {
        UxmActions actions = mock(UxmActions.class);
        when(actions.scoreboard()).thenReturn(Optional.of(scoreboard));
        return actions;
    }
}
