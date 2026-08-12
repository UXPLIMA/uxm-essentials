package com.uxplima.uxmessentials.shared.adapter.outbound.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;

import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.format.NamedTextColor;

import com.uxplima.uxmessentials.playerstate.domain.GlowColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the {@link PlayerTeamCoordinator} against MockBukkit's scoreboard/team API. The name-hiding half: {@code hide}
 * parks the wearer in a never-show-name-tag {@code uxm-namehide} team on their current board, {@code show} drops it,
 * {@code reapply} survives a board switch by re-creating the team on a fresh board, and the registration is idempotent
 * so the board-switch callback may fire every switch. The glow-colour half: a colour moves the player into a team
 * carrying that colour, the two states combine into one team when both are set, and clearing takes both away.
 */
class PlayerTeamCoordinatorTest {

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
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();

        coordinator.hide(wearer);

        Team team = wearer.getScoreboard().getTeam(PlayerTeamCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.getOption(Team.Option.NAME_TAG_VISIBILITY)).isEqualTo(Team.OptionStatus.NEVER);
        assertThat(team.hasEntry(wearer.getName())).isTrue();
        assertThat(coordinator.isHidden(wearer.getUniqueId())).isTrue();
    }

    @Test
    void showRemovesTheWearersEntryFromTheHideTeam() {
        PlayerMock wearer = server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        coordinator.hide(wearer);

        coordinator.show(wearer);

        Team team = wearer.getScoreboard().getTeam(PlayerTeamCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(wearer.getName())).isFalse();
        assertThat(coordinator.isHidden(wearer.getUniqueId())).isFalse();
    }

    @Test
    void reapplyOnAFreshBoardRecreatesTheHideTeamWithTheWearer() {
        PlayerMock wearer = server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        coordinator.hide(wearer);

        // Simulate the scoreboard module switching the player onto a brand-new per-player board: setScoreboard resets
        // the client team registry, so the new board carries no hide-team until the board-switch callback re-applies.
        Scoreboard freshBoard = server.getScoreboardManager().getNewScoreboard();
        assertThat(freshBoard.getTeam(PlayerTeamCoordinator.TEAM_NAME)).isNull();

        coordinator.reapply(wearer, freshBoard);

        Team team = freshBoard.getTeam(PlayerTeamCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.getOption(Team.Option.NAME_TAG_VISIBILITY)).isEqualTo(Team.OptionStatus.NEVER);
        assertThat(team.hasEntry(wearer.getName())).isTrue();
    }

    @Test
    void reapplyIsIdempotentSoTheBoardSwitchCallbackMayFireRepeatedly() {
        PlayerMock wearer = server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        coordinator.hide(wearer);
        Scoreboard board = wearer.getScoreboard();

        // A re-register of an existing team throws; reapply must tolerate being called again on the same board.
        assertThatCode(() -> {
                    coordinator.reapply(wearer, board);
                    coordinator.reapply(wearer, board);
                })
                .doesNotThrowAnyException();
        Team team = board.getTeam(PlayerTeamCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(wearer.getName())).isTrue();
    }

    @Test
    void reapplyForAPlayerWithNeitherStateRegistersNoTeamAtAll() {
        PlayerMock wearer = server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        Scoreboard board = wearer.getScoreboard();

        coordinator.reapply(wearer, board);

        // Nothing to express, so nothing is registered: a player who neither hides their name nor glows in a colour
        // stays outside every team of ours and keeps whatever a foreign plugin put them in.
        assertThat(board.getTeam(PlayerTeamCoordinator.TEAM_NAME)).isNull();
        assertThat(board.getTeams()).noneMatch(team -> team.hasEntry(wearer.getName()));
    }

    @Test
    void colourPutsTheGlowingPlayerInATeamCarryingThatColour() {
        PlayerMock player = server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();

        coordinator.colour(player, GlowColor.RED);

        assertThat(coordinator.colourOf(player.getUniqueId())).isEqualTo(GlowColor.RED);
        Team team = teamOf(player);
        assertThat(team.color()).isEqualTo(NamedTextColor.RED);
        // Not hidden, so the vanilla above-head name must stay on: the colour alone never silences a name.
        assertThat(team.getOption(Team.Option.NAME_TAG_VISIBILITY)).isEqualTo(Team.OptionStatus.ALWAYS);
    }

    @Test
    void recolouringMovesThePlayerOutOfThePreviousColourTeam() {
        PlayerMock player = server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        coordinator.colour(player, GlowColor.RED);
        Team red = teamOf(player);

        coordinator.colour(player, GlowColor.AQUA);

        assertThat(red.hasEntry(player.getName())).isFalse();
        assertThat(teamOf(player).color()).isEqualTo(NamedTextColor.AQUA);
    }

    @Test
    void theDefaultColourTakesThePlayerBackOutOfEveryColourTeam() {
        PlayerMock player = server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        coordinator.colour(player, GlowColor.GOLD);

        coordinator.colour(player, GlowColor.DEFAULT);

        assertThat(coordinator.colourOf(player.getUniqueId())).isEqualTo(GlowColor.DEFAULT);
        assertThat(isInATeamOfOurs(player)).isFalse();
    }

    @Test
    void aHiddenWearerCanStillGlowInAColour() {
        PlayerMock wearer = server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        coordinator.hide(wearer);

        coordinator.colour(wearer, GlowColor.BLUE);

        // One team per player per board, so both states have to be expressed by the same team.
        Team team = teamOf(wearer);
        assertThat(team.color()).isEqualTo(NamedTextColor.BLUE);
        assertThat(team.getOption(Team.Option.NAME_TAG_VISIBILITY)).isEqualTo(Team.OptionStatus.NEVER);
        assertThat(coordinator.isHidden(wearer.getUniqueId())).isTrue();
    }

    @Test
    void showingAColouredWearerKeepsTheColourAndBringsTheNameBack() {
        PlayerMock wearer = server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        coordinator.hide(wearer);
        coordinator.colour(wearer, GlowColor.GREEN);

        coordinator.show(wearer);

        Team team = teamOf(wearer);
        assertThat(team.color()).isEqualTo(NamedTextColor.GREEN);
        assertThat(team.getOption(Team.Option.NAME_TAG_VISIBILITY)).isEqualTo(Team.OptionStatus.ALWAYS);
    }

    @Test
    void clearUnmarksWithoutTouchingABoard() {
        PlayerMock wearer = server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        coordinator.hide(wearer);

        coordinator.clear(wearer.getUniqueId());

        assertThat(coordinator.isHidden(wearer.getUniqueId())).isFalse();
        // The UUID-only clear is for an offline/cross-thread caller and leaves the board entry in place; a later
        // reapply with the now-unmarked state drops it.
        coordinator.reapply(wearer, wearer.getScoreboard());
        Team team = wearer.getScoreboard().getTeam(PlayerTeamCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(wearer.getName())).isFalse();
    }

    @Test
    void clearWithThePlayerUnmarksAndRemovesTheBoardEntry() {
        PlayerMock wearer = server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        coordinator.hide(wearer);

        coordinator.clear(wearer);

        assertThat(coordinator.isHidden(wearer.getUniqueId())).isFalse();
        // The player overload drops the stranded entry from the board itself, not just the bookkeeping.
        Team team = wearer.getScoreboard().getTeam(PlayerTeamCoordinator.TEAM_NAME);
        assertThat(team).isNotNull();
        assertThat(team.hasEntry(wearer.getName())).isFalse();
    }

    @Test
    void clearWithThePlayerAlsoDropsTheGlowColour() {
        PlayerMock player = server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        coordinator.colour(player, GlowColor.YELLOW);

        coordinator.clear(player);

        // Neither the glowing flag nor its colour survives a restart, so the join handler's clear must leave the
        // player wearing no colour and sitting in no team of ours.
        assertThat(coordinator.colourOf(player.getUniqueId())).isEqualTo(GlowColor.DEFAULT);
        assertThat(isInATeamOfOurs(player)).isFalse();
    }

    @Test
    void clearWithThePlayerLeavesNoStaleEntryOnTheMainBoard() {
        PlayerMock wearer = server.addPlayer();
        // The main shared board is what player.getScoreboard() returns by default and is a server-lifetime singleton,
        // so its team entries do not die when the player quits. Hiding then clearing must leave no stale entry behind.
        wearer.setScoreboard(server.getScoreboardManager().getMainScoreboard());
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();
        coordinator.hide(wearer);
        Team mainTeam = server.getScoreboardManager().getMainScoreboard().getTeam(PlayerTeamCoordinator.TEAM_NAME);
        assertThat(mainTeam).isNotNull();
        assertThat(mainTeam.hasEntry(wearer.getName())).isTrue();

        coordinator.clear(wearer);

        assertThat(mainTeam.hasEntry(wearer.getName())).isFalse();
    }

    @Test
    void clearWithThePlayerIsASafeNoOpWhenNoEntryExists() {
        PlayerMock wearer = server.addPlayer();
        PlayerTeamCoordinator coordinator = new PlayerTeamCoordinator();

        // Never hidden and never coloured: there is no team and no entry, so the clear must not throw.
        assertThatCode(() -> coordinator.clear(wearer)).doesNotThrowAnyException();
        assertThat(coordinator.isHidden(wearer.getUniqueId())).isFalse();
    }

    /** The team of ours the player currently sits in on their own board; fails the test when they sit in none. */
    private static Team teamOf(PlayerMock player) {
        return findTeam(player).orElseThrow(() -> new AssertionError("the player sits in no team of ours"));
    }

    /** Whether the player sits in any team of ours at all. */
    private static boolean isInATeamOfOurs(PlayerMock player) {
        return findTeam(player).isPresent();
    }

    private static Optional<Team> findTeam(PlayerMock player) {
        for (Team team : player.getScoreboard().getTeams()) {
            if (team.getName().startsWith("uxm-") && team.hasEntry(player.getName())) {
                return Optional.of(team);
            }
        }
        return Optional.empty();
    }
}
