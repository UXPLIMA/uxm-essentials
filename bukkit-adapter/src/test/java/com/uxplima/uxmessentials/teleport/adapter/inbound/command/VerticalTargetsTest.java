package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Where {@code /bottom} and {@code /up} put a player.
 *
 * <p>On a live flat world {@code /bottom} put the player one block above the bedrock, inside the dirt, and
 * {@code /up 5} put them five blocks up in the air with nothing under them, so they fell straight back down. The
 * command says it puts the player on a placed block.
 */
class VerticalTargetsTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new WorldMock(Material.DIRT, 4);
        server.addWorld(world);
        player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 5, 0.5));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("/bottom stands the player in the lowest open space, never inside a block")
    void bottomFindsTheLowestOpenSpace() {
        world.getBlockAt(0, 1, 0).setType(Material.AIR);
        world.getBlockAt(0, 2, 0).setType(Material.AIR);

        Optional<Position> target = VerticalCommand.bottomTarget(player, player.getLocation());

        assertThat(target).hasValueSatisfying(at -> assertThat(at.y()).isEqualTo(1.0));
        assertThat(world.getBlockAt(0, 0, 0).getType().isSolid()).isTrue();
    }

    @Test
    @DisplayName("/bottom over solid ground stands the player on the ground they are over")
    void bottomOverSolidGroundStaysOnTheSurface() {
        double surface = world.getHighestBlockYAt(0, 0) + 1;

        Optional<Position> target = VerticalCommand.bottomTarget(player, player.getLocation());

        assertThat(target).hasValueSatisfying(at -> assertThat(at.y()).isEqualTo(surface));
    }

    @Test
    @DisplayName("/up puts a block under the spot it sends the player to")
    void upPlacesAFloor() {
        Optional<Position> target = VerticalCommand.upTarget(player, player.getLocation(), 5);

        assertThat(target).hasValueSatisfying(at -> assertThat(at.y()).isEqualTo(10.0));
        assertThat(world.getBlockAt(0, 9, 0).getType()).isEqualTo(Material.GLASS);
    }

    @Test
    @DisplayName("/up keeps a block already there and does not build past the top of the world")
    void upKeepsAnExistingFloorAndStopsAtTheTop() {
        world.getBlockAt(0, 9, 0).setType(Material.STONE);

        assertThat(VerticalCommand.upTarget(player, player.getLocation(), 5)).isPresent();
        assertThat(world.getBlockAt(0, 9, 0).getType()).isEqualTo(Material.STONE);
        assertThat(VerticalCommand.upTarget(player, player.getLocation(), world.getMaxHeight()))
                .isEmpty();
    }
}
