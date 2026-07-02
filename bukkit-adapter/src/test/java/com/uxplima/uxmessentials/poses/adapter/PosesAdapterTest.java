package com.uxplima.uxmessentials.poses.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.poses.adapter.inbound.listener.PlayerSitInteractListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.PoseCancelListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.SeatInteractListener;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitPoseReturn;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitSeatPort;
import com.uxplima.uxmessentials.poses.adapter.outbound.PdcPlayerSitPreferences;
import com.uxplima.uxmessentials.poses.application.AllowAllRegionGate;
import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.StartPlayerSit;
import com.uxplima.uxmessentials.poses.application.StartSit;
import com.uxplima.uxmessentials.poses.application.StopPose;
import com.uxplima.uxmessentials.poses.application.TogglePlayerSit;
import com.uxplima.uxmessentials.poses.domain.SittableBlocks;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.StorePosesPlaceholders;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the poses adapter end-to-end over a real {@link BukkitSeatPort}: right-clicking a stair
 * seats the player on a tagged, non-persistent armour stand; right-clicking a player stacks them on as a passenger;
 * the cancel triggers (quit, sneak, damage, teleport) end the pose and remove the seat; {@code sweepOrphans} reaps a
 * stray tagged seat; and the {@code poses_sitting} / {@code poses_toggle} placeholders track the live state.
 *
 * <p>The ghost-prevention proof is {@link #quitRemovesTheSeatSoNoGhostRemains()}: after the seated player quits,
 * the world holds <em>zero</em> entities carrying the {@code poses_seat} tag. The stacking-cleanup proof is
 * {@link #whenTheCarrierQuitsTheRidersSessionIsClearedAndTheyAreNoLongerAPassenger()}: a carrier leaving ends its
 * rider's session and takes the rider off as a passenger.
 */
class PosesAdapterTest {

    private ServerMock server;
    private WorldMock world;
    private org.bukkit.plugin.Plugin plugin;
    private NamespacedKey seatKey;

    private PoseSessions sessions;
    private BukkitSeatPort seats;
    private StartSit startSit;
    private StopPose stopPose;
    private SeatInteractListener interactListener;
    private PlayerSitInteractListener playerSitInteractListener;
    private PoseCancelListener cancelListener;
    private PdcPlayerSitPreferences playerSitPreferences;
    private TogglePlayerSit togglePlayerSit;
    private StorePosesPlaceholders placeholders;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        seatKey = new NamespacedKey(plugin, "poses_seat");

        Scheduler scheduler = new InlineScheduler();
        sessions = new PoseSessions();
        seats = new BukkitSeatPort(plugin, scheduler, new NoopLogger());
        SittableBlocks sittableBlocks = new SittableBlocks(List.of("*_STAIRS", "*_SLAB", "*_CARPET"));
        PlayerLocator locator = who -> Optional.ofNullable(server.getPlayer(who.uuid()))
                .map(p -> BukkitRefs.toPosition(Objects.requireNonNull(p.getLocation(), "location")));
        DomainEventPublisher events = event -> {};

        startSit =
                new StartSit(sessions, seats, new AllowAllRegionGate(), locator, events, Clock.systemUTC(), true, true);
        playerSitPreferences = new PdcPlayerSitPreferences();
        StartPlayerSit startPlayerSit =
                new StartPlayerSit(sessions, seats, playerSitPreferences, locator, events, Clock.systemUTC(), true);
        togglePlayerSit = new TogglePlayerSit(playerSitPreferences);
        stopPose = new StopPose(sessions, seats, new BukkitPoseReturn(plugin, scheduler), events, true);
        interactListener = new SeatInteractListener(startSit, seats, sittableBlocks, new KeyMessages(), true, 5.0);
        playerSitInteractListener = new PlayerSitInteractListener(startPlayerSit, new KeyMessages());
        cancelListener = new PoseCancelListener(stopPose, sessions);
        placeholders = new StorePosesPlaceholders(sessions, playerSitPreferences);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rightClickingAStairSeatsThePlayerOnATaggedSeat() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        Block stair = stairAt(1, 64, 0);

        interactListener.onInteract(rightClick(player, stair));

        List<Entity> tagged = taggedSeats();
        assertThat(tagged).hasSize(1);
        assertThat(tagged.get(0)).isInstanceOf(ArmorStand.class);
        assertThat(tagged.get(0).getPassengers()).contains(player);
        assertThat(sessions.isPosing(BukkitRefs.toRef(player))).isTrue();
    }

    @Test
    void quitRemovesTheSeatSoNoGhostRemains() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));
        assertThat(taggedSeats()).hasSize(1);

        cancelListener.onQuit(
                new PlayerQuitEvent(player, Component.text("bye"), PlayerQuitEvent.QuitReason.DISCONNECTED));

        // The ghost-prevention proof: not one tagged seat entity is left in the world after the seated player quits.
        assertThat(taggedSeats()).isEmpty();
        assertThat(sessions.isPosing(BukkitRefs.toRef(player))).isFalse();
    }

    @Test
    void sweepOrphansRemovesAStrayTaggedSeat() {
        ArmorStand stray = world.spawn(new Location(world, 5, 64, 5), ArmorStand.class);
        stray.getPersistentDataContainer().set(seatKey, PersistentDataType.STRING, "stray");
        assertThat(taggedSeats()).hasSize(1);

        int removed = seats.sweepOrphans();

        assertThat(removed).isEqualTo(1);
        assertThat(taggedSeats()).isEmpty();
    }

    @Test
    void sneakingEndsThePoseAndReturnsThePlayerToWhereTheySat() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));
        // Move the player away from where they sat; standing up must teleport them back to the captured start.
        player.teleport(new Location(world, 20, 70, 20));

        cancelListener.onSneak(new PlayerToggleSneakEvent(player, true));

        assertThat(sessions.isPosing(BukkitRefs.toRef(player))).isFalse();
        assertThat(taggedSeats()).isEmpty();
        Location back = Objects.requireNonNull(player.getLocation(), "location");
        assertThat(back.getX()).isEqualTo(0.5);
        assertThat(back.getZ()).isEqualTo(0.5);
    }

    @Test
    void takingDamageEndsThePoseAndRemovesTheSeat() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerMock attacker = server.addPlayer("Attacker");
        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));

        EntityDamageEvent damage = player.simulateDamage(1.0, attacker);
        cancelListener.onDamage(damage);

        assertThat(sessions.isPosing(BukkitRefs.toRef(player))).isFalse();
        assertThat(taggedSeats()).isEmpty();
    }

    @Test
    void teleportingEndsThePoseAndRemovesTheSeat() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));

        cancelListener.onTeleport(
                new PlayerTeleportEvent(player, new Location(world, 0.5, 64, 0.5), new Location(world, 30, 64, 30)));

        assertThat(sessions.isPosing(BukkitRefs.toRef(player))).isFalse();
        assertThat(taggedSeats()).isEmpty();
    }

    @Test
    void theSittingPlaceholderTracksTheLiveSession() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        assertThat(placeholders.sitting(who)).isFalse();

        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));
        assertThat(placeholders.sitting(who)).isTrue();

        stopPose.stop(who);
        assertThat(placeholders.sitting(who)).isFalse();
    }

    @Test
    void rightClickingAPlayerSeatsTheClickerOnThemAsAPassenger() {
        PlayerMock rider = playerAt(0.5, 64, 0.5);
        rider.addAttachment(plugin, "uxmessentials.playersit.use", true);
        PlayerMock target = server.addPlayer("Carrier");

        playerSitInteractListener.onInteract(rightClickPlayer(rider, target));

        // Stacking mount: the clicker is now a passenger of the target (addPassenger chains for A-on-B-on-C).
        assertThat(target.getPassengers()).contains(rider);
        assertThat(sessions.isPosing(BukkitRefs.toRef(rider))).isTrue();
    }

    @Test
    void aRefusingTargetRejectsThePlayerSit() {
        PlayerMock rider = playerAt(0.5, 64, 0.5);
        rider.addAttachment(plugin, "uxmessentials.playersit.use", true);
        PlayerMock target = server.addPlayer("Carrier");
        // The target flips their /poses toggle to refuse being sat on.
        togglePlayerSit.toggle(BukkitRefs.toRef(target));

        playerSitInteractListener.onInteract(rightClickPlayer(rider, target));

        assertThat(target.getPassengers()).doesNotContain(rider);
        assertThat(sessions.isPosing(BukkitRefs.toRef(rider))).isFalse();
    }

    @Test
    void togglingFlipsThePdcBackedPreferenceAndThePlaceholderReflectsIt() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        // The GSit default is to allow being sat on.
        assertThat(placeholders.allowsSitting(who)).isTrue();

        assertThat(togglePlayerSit.toggle(who)).isFalse();
        assertThat(placeholders.allowsSitting(who)).isFalse();

        assertThat(togglePlayerSit.toggle(who)).isTrue();
        assertThat(placeholders.allowsSitting(who)).isTrue();
    }

    @Test
    void whenTheCarrierQuitsTheRidersSessionIsClearedAndTheyAreNoLongerAPassenger() {
        PlayerMock rider = playerAt(0.5, 64, 0.5);
        rider.addAttachment(plugin, "uxmessentials.playersit.use", true);
        PlayerMock target = server.addPlayer("Carrier");
        playerSitInteractListener.onInteract(rightClickPlayer(rider, target));
        assertThat(target.getPassengers()).contains(rider);

        cancelListener.onQuit(
                new PlayerQuitEvent(target, Component.text("bye"), PlayerQuitEvent.QuitReason.DISCONNECTED));

        // Stacking cleanup proof: the carrier leaving ends the rider's session and takes them off as a passenger,
        // so no stuck PoseSession and no ghost passenger remain.
        assertThat(sessions.isPosing(BukkitRefs.toRef(rider))).isFalse();
        assertThat(rider.getVehicle()).isNull();
        assertThat(target.getPassengers()).doesNotContain(rider);
    }

    @Test
    void removeAllDrainsEverySeatOnStop() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        interactListener.onInteract(rightClick(player, stairAt(1, 64, 0)));
        assertThat(taggedSeats()).hasSize(1);

        seats.removeAll();

        assertThat(taggedSeats()).isEmpty();
    }

    private PlayerMock playerAt(double x, double y, double z) {
        PlayerMock player = server.addPlayer("Steve");
        player.teleport(new Location(world, x, y, z));
        return player;
    }

    private Block stairAt(int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.OAK_STAIRS);
        return block;
    }

    private static PlayerInteractEvent rightClick(PlayerMock player, Block block) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND);
    }

    private static PlayerInteractEntityEvent rightClickPlayer(PlayerMock clicker, PlayerMock target) {
        return new PlayerInteractEntityEvent(clicker, target, EquipmentSlot.HAND);
    }

    /** Every entity in the world that carries the {@code poses_seat} PDC tag — the ghost-prevention probe. */
    private List<Entity> taggedSeats() {
        return world.getEntities().stream()
                .filter(entity -> entity.getPersistentDataContainer().has(seatKey, PersistentDataType.STRING))
                .toList();
    }

    /** Runs every scheduled hop inline so the region-threaded seat work completes within the test. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Resolves each key to its own id, enough for the command-feedback paths the tests do not assert on. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
