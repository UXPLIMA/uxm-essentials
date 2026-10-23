package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.holograms.adapter.outbound.PacketHologramView;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A board is read from further off than a client sends a click on an entity, so the server works out what a click
 * in the air was aimed at. A click that lands on a board turns it and does nothing else: the item in hand is not
 * used and the block behind is not touched.
 */
class HologramAimListenerTest {

    private ServerMock server;
    private World world;
    private PlayerMock alice;
    private final List<String> clicked = new ArrayList<>();
    private final List<Double> reachAsked = new ArrayList<>();
    private Optional<PacketHologramView.Aimed> aimed = Optional.empty();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        alice.teleport(new Location(world, 0.5, 64, 0.5, -90f, 0f));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aClickAimedAtABoardTurnsItAndUsesNothingElse() {
        aimed = Optional.of(new PacketHologramView.Aimed(HologramName.of("parkour-top"), 9.0));
        PlayerInteractEvent click = click(Action.RIGHT_CLICK_AIR, null);

        listener(8).onInteract(click);

        assertThat(clicked).containsExactly("Alice:parkour-top:false");
        assertThat(click.useItemInHand()).isEqualTo(Event.Result.DENY);
        assertThat(click.useInteractedBlock()).isEqualTo(Event.Result.DENY);
        assertThat(reachAsked).containsExactly(8.0);
    }

    @Test
    void aLeftClickIsAnAttack() {
        aimed = Optional.of(new PacketHologramView.Aimed(HologramName.of("parkour-top"), 9.0));

        listener(8).onInteract(click(Action.LEFT_CLICK_AIR, null));

        assertThat(clicked).containsExactly("Alice:parkour-top:true");
    }

    @Test
    void aClickAimedAtNothingIsLeftAlone() {
        PlayerInteractEvent click = click(Action.RIGHT_CLICK_AIR, null);

        listener(8).onInteract(click);

        assertThat(clicked).isEmpty();
        assertThat(click.useItemInHand()).isEqualTo(Event.Result.DEFAULT);
    }

    /** The block a click names stands between the player and the board, so the click is the block's. */
    @Test
    void aBlockInFrontOfTheBoardKeepsItsClick() {
        aimed = Optional.of(new PacketHologramView.Aimed(HologramName.of("parkour-top"), 9.0));
        Block wall = world.getBlockAt(3, 65, 0);
        wall.setType(Material.STONE);

        listener(8).onInteract(click(Action.RIGHT_CLICK_BLOCK, wall));

        assertThat(clicked).isEmpty();
    }

    @Test
    void aBlockBehindTheBoardDoesNotTakeTheClick() {
        aimed = Optional.of(new PacketHologramView.Aimed(HologramName.of("parkour-top"), 2.0));
        Block wall = world.getBlockAt(4, 65, 0);
        wall.setType(Material.STONE);

        listener(8).onInteract(click(Action.RIGHT_CLICK_BLOCK, wall));

        assertThat(clicked).containsExactly("Alice:parkour-top:false");
    }

    @Test
    void stepsOnAPlateAreNotClicks() {
        aimed = Optional.of(new PacketHologramView.Aimed(HologramName.of("parkour-top"), 9.0));

        listener(8).onInteract(click(Action.PHYSICAL, world.getBlockAt(0, 63, 0)));

        assertThat(clicked).isEmpty();
    }

    @Test
    void aReachOfNothingTurnsTheAimOff() {
        aimed = Optional.of(new PacketHologramView.Aimed(HologramName.of("parkour-top"), 9.0));

        listener(0).onInteract(click(Action.RIGHT_CLICK_AIR, null));

        assertThat(clicked).isEmpty();
        assertThat(reachAsked).isEmpty();
    }

    private HologramAimListener listener(double reach) {
        return new HologramAimListener(
                (player, asked) -> {
                    reachAsked.add(asked);
                    return aimed;
                },
                (player, name, attack) -> clicked.add(player.getName() + ":" + name.value() + ":" + attack),
                reach);
    }

    private PlayerInteractEvent click(Action action, @Nullable Block block) {
        return new PlayerInteractEvent(alice, action, new ItemStack(Material.COMPASS), block, BlockFace.WEST);
    }
}
