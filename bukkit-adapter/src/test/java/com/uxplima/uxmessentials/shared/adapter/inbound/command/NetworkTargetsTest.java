package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import org.junit.jupiter.api.Test;

/**
 * A command that names a player who is on another server is told which server, by account or by the name a live
 * player has there, and never about a player the sender may not see.
 */
class NetworkTargetsTest {

    private static final OnlinePlayer REMOTE =
            new OnlinePlayer(new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000aa"), "Remy"), "lobby");
    private static final OnlinePlayer LOCAL = new OnlinePlayer(
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000bb"), "Lena"), "survival");

    private final Roster roster = new Roster();
    private final List<PlayerRef> hidden = new ArrayList<>();
    private final NetworkTargets targets = new NetworkTargets(
            new NobodyHere(),
            new Idle(),
            roster,
            (source, target) -> !hidden.contains(target),
            new CommandFeedback((viewer, key, placeholders) -> key.key()));

    @Test
    void aPlayerOnAnotherServerIsNamedWithThatServer() {
        roster.players.add(REMOTE);
        MessageLine expected =
                new MessageLine(SharedMessageKey.COMMAND_TARGET_ELSEWHERE, Map.of("player", "Remy", "server", "lobby"));

        assertThat(targets.elsewhere(new NoSource(), "remy")).contains(expected);
        assertThat(targets.elsewhere(new NoSource(), REMOTE.player())).contains(expected);
    }

    @Test
    void aPlayerOnThisServerIsNotSentElsewhere() {
        roster.players.add(LOCAL);

        assertThat(targets.elsewhere(new NoSource(), "Lena")).isEmpty();
        assertThat(targets.elsewhere(new NoSource(), LOCAL.player())).isEmpty();
    }

    @Test
    void aPlayerTheSenderMayNotSeeIsNotLocated() {
        roster.players.add(REMOTE);
        hidden.add(REMOTE.player());

        assertThat(targets.elsewhere(new NoSource(), "Remy")).isEmpty();
        assertThat(targets.elsewhere(new NoSource(), REMOTE.player())).isEmpty();
    }

    @Test
    void aPlayerOnlineNowhereIsNotLocated() {
        assertThat(targets.elsewhere(new NoSource(), "Remy")).isEmpty();
        assertThat(targets.elsewhere(new NoSource(), REMOTE.player())).isEmpty();
    }

    /** A server with nobody connected. */
    private static final class NobodyHere implements PlayerLookup {

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    /** A scheduler that never runs anything: these checks are memory reads made where the caller is. */
    private static final class Idle implements Scheduler {

        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}
    }

    /** The network roster: who is online on which backend, names matched in any case. */
    private static final class Roster implements NetworkPresence {

        final List<OnlinePlayer> players = new ArrayList<>();

        @Override
        public String localServer() {
            return "survival";
        }

        @Override
        public Optional<OnlinePlayer> find(UUID player) {
            return players.stream()
                    .filter(online -> online.player().uuid().equals(player))
                    .findFirst();
        }

        @Override
        public Optional<OnlinePlayer> findByName(String name) {
            return players.stream()
                    .filter(online -> online.player().name().equalsIgnoreCase(name))
                    .findFirst();
        }

        @Override
        public NameMatch matchByName(String name) {
            return NameMatch.of(findByName(name).map(OnlinePlayer::player));
        }

        @Override
        public List<OnlinePlayer> onServer(String server) {
            return players.stream()
                    .filter(online -> online.server().equals(server))
                    .toList();
        }

        @Override
        public List<OnlinePlayer> online() {
            return List.copyOf(players);
        }
    }

    /** The command runs without a sender: the visibility rule above judges by the target alone. */
    private static final class NoSource implements CommandSourceStack {

        @Override
        public Location getLocation() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommandSender getSender() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entity getExecutor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Player getPlayerOrThrow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entity getEntityOrThrow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommandSourceStack withLocation(Location location) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommandSourceStack withExecutor(Entity executor) {
            throw new UnsupportedOperationException();
        }
    }
}
