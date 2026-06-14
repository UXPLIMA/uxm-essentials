package com.uxplima.uxmessentials.shared.adapter.outbound.nametag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the {@link NameVisibilityCoordinator} against MockBukkit's scoreboard/team API: {@code hide} parks the
 * wearer's name in a never-show-name-tag {@code uxm-namehide} team on their current board, {@code show} drops it,
 * {@code reapply} survives a board switch by re-creating the team on a fresh board, and the registration is idempotent
 * so the board-switch callback may fire every switch.
 */
class NameVisibilityCoordinatorTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void hidePlacesTheWearerInANeverShowNameTagTeamOnTheirBoard() {
        PlayerMock wearer = server.addPlayer();
        NameVisibilityCoordinator coordinator = new NameVisibilityCoordinator();

        coordinator.hide(wearer);

        Team team = wearer.getScoreboard().getTeam(NameVisibilityCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.getOption(Team.Option.NAME_TAG_VISIBILITY)).isEqualTo(Team.OptionStatus.NEVER);
        assertThat(team.hasEntry(wearer.getName())).isTrue();
        assertThat(coordinator.isHidden(wearer.getUniqueId())).isTrue();
    }

    @Test
    void showRemovesTheWearersEntryFromTheHideTeam() {
        PlayerMock wearer = server.addPlayer();
        NameVisibilityCoordinator coordinator = new NameVisibilityCoordinator();
        coordinator.hide(wearer);

        coordinator.show(wearer);

        Team team = wearer.getScoreboard().getTeam(NameVisibilityCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(wearer.getName())).isFalse();
        assertThat(coordinator.isHidden(wearer.getUniqueId())).isFalse();
    }

    @Test
    void reapplyOnAFreshBoardRecreatesTheHideTeamWithTheWearer() {
        PlayerMock wearer = server.addPlayer();
        NameVisibilityCoordinator coordinator = new NameVisibilityCoordinator();
        coordinator.hide(wearer);

        // Simulate the scoreboard module switching the player onto a brand-new per-player board: setScoreboard resets
        // the client team registry, so the new board carries no hide-team until the board-switch callback re-applies.
        Scoreboard freshBoard = server.getScoreboardManager().getNewScoreboard();
        assertThat(freshBoard.getTeam(NameVisibilityCoordinator.TEAM_NAME)).isNull();

        coordinator.reapply(wearer, freshBoard);

        Team team = freshBoard.getTeam(NameVisibilityCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.getOption(Team.Option.NAME_TAG_VISIBILITY)).isEqualTo(Team.OptionStatus.NEVER);
        assertThat(team.hasEntry(wearer.getName())).isTrue();
    }

    @Test
    void reapplyIsIdempotentSoTheBoardSwitchCallbackMayFireRepeatedly() {
        PlayerMock wearer = server.addPlayer();
        NameVisibilityCoordinator coordinator = new NameVisibilityCoordinator();
        coordinator.hide(wearer);
        Scoreboard board = wearer.getScoreboard();

        // A re-register of an existing team throws; reapply must tolerate being called again on the same board.
        assertThatCode(() -> {
                    coordinator.reapply(wearer, board);
                    coordinator.reapply(wearer, board);
                })
                .doesNotThrowAnyException();
        Team team = board.getTeam(NameVisibilityCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(wearer.getName())).isTrue();
    }

    @Test
    void reapplyForANonHiddenWearerEnsuresTheTeamButDropsTheEntry() {
        PlayerMock wearer = server.addPlayer();
        NameVisibilityCoordinator coordinator = new NameVisibilityCoordinator();
        Scoreboard board = wearer.getScoreboard();

        coordinator.reapply(wearer, board);

        Team team = board.getTeam(NameVisibilityCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(wearer.getName())).isFalse();
    }

    @Test
    void clearUnmarksWithoutTouchingABoard() {
        PlayerMock wearer = server.addPlayer();
        NameVisibilityCoordinator coordinator = new NameVisibilityCoordinator();
        coordinator.hide(wearer);

        coordinator.clear(wearer.getUniqueId());

        assertThat(coordinator.isHidden(wearer.getUniqueId())).isFalse();
        // The UUID-only clear is for an offline/cross-thread caller and leaves the board entry in place; a later
        // reapply
        // with the now-unmarked state drops it.
        coordinator.reapply(wearer, wearer.getScoreboard());
        Team team = wearer.getScoreboard().getTeam(NameVisibilityCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(wearer.getName())).isFalse();
    }

    @Test
    void clearWithThePlayerUnmarksAndRemovesTheBoardEntry() {
        PlayerMock wearer = server.addPlayer();
        NameVisibilityCoordinator coordinator = new NameVisibilityCoordinator();
        coordinator.hide(wearer);

        coordinator.clear(wearer);

        assertThat(coordinator.isHidden(wearer.getUniqueId())).isFalse();
        // The player overload drops the stranded entry from the board itself, not just the bookkeeping.
        Team team = wearer.getScoreboard().getTeam(NameVisibilityCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(wearer.getName())).isFalse();
    }

    @Test
    void clearWithThePlayerLeavesNoStaleEntryOnTheMainBoard() {
        PlayerMock wearer = server.addPlayer();
        // The main shared board is what player.getScoreboard() returns by default and is a server-lifetime singleton,
        // so
        // its team entries do not die when the player quits. Hiding then clearing must leave no stale entry behind.
        wearer.setScoreboard(server.getScoreboardManager().getMainScoreboard());
        NameVisibilityCoordinator coordinator = new NameVisibilityCoordinator();
        coordinator.hide(wearer);
        Team mainTeam = server.getScoreboardManager().getMainScoreboard().getTeam(NameVisibilityCoordinator.TEAM_NAME);
        assertThat(mainTeam).isNotNull();
        assertThat(mainTeam.hasEntry(wearer.getName())).isTrue();

        coordinator.clear(wearer);

        assertThat(mainTeam.hasEntry(wearer.getName())).isFalse();
    }

    @Test
    void clearWithThePlayerIsASafeNoOpWhenNoEntryExists() {
        PlayerMock wearer = server.addPlayer();
        NameVisibilityCoordinator coordinator = new NameVisibilityCoordinator();

        // Never hidden: there is no hide-team and no entry, so the clear must not throw.
        assertThatCode(() -> coordinator.clear(wearer)).doesNotThrowAnyException();
        assertThat(coordinator.isHidden(wearer.getUniqueId())).isFalse();
    }
}
