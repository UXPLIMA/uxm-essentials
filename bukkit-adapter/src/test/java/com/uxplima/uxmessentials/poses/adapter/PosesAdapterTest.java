package com.uxplima.uxmessentials.poses.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.poses.adapter.inbound.listener.CrawlMoveListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.PlayerSitInteractListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.PoseCancelListener;
import com.uxplima.uxmessentials.poses.adapter.inbound.listener.SeatInteractListener;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitPacketPosePort;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitPoseReturn;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitSeatPort;
import com.uxplima.uxmessentials.poses.adapter.outbound.BukkitSnores;
import com.uxplima.uxmessentials.poses.adapter.outbound.PdcPlayerSitPreferences;
import com.uxplima.uxmessentials.poses.application.AllowAllRegionGate;
import com.uxplima.uxmessentials.poses.application.CrawlSessions;
import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.StartCrawl;
import com.uxplima.uxmessentials.poses.application.StartPlayerSit;
import com.uxplima.uxmessentials.poses.application.StartPose;
import com.uxplima.uxmessentials.poses.application.StartSit;
import com.uxplima.uxmessentials.poses.application.StopPose;
import com.uxplima.uxmessentials.poses.application.TogglePlayerSit;
import com.uxplima.uxmessentials.poses.application.port.CrawlView;
import com.uxplima.uxmessentials.poses.domain.PoseType;
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
import com.uxplima.uxmlib.packet.npc.NpcPackets;
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
    private CrawlSessions crawlSessions;
    private BukkitSeatPort seats;
    private BukkitPacketPosePort posePort;
    private BukkitSnores snores;
    private RecordingCrawlView crawlView;
    private StartSit startSit;
    private StartPose startPose;
    private StartCrawl startCrawl;
    private StopPose stopPose;
    private SeatInteractListener interactListener;
    private PlayerSitInteractListener playerSitInteractListener;
    private PoseCancelListener cancelListener;
    private CrawlMoveListener crawlMoveListener;
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
        crawlSessions = new CrawlSessions();
        crawlView = new RecordingCrawlView();
        seats = new BukkitSeatPort(plugin, scheduler, new NoopLogger());
        SittableBlocks sittableBlocks = new SittableBlocks(List.of("*_STAIRS", "*_SLAB", "*_CARPET"));
        PlayerLocator locator = who -> Optional.ofNullable(server.getPlayer(who.uuid()))
                .map(p -> BukkitRefs.toPosition(Objects.requireNonNull(p.getLocation(), "location")));
        DomainEventPublisher events = event -> {};

        // A spin step of 30 degrees per pass makes the seat's yaw advance visibly across the ticks the spin test
        // drives; the snore loop is exercised only through isSnoring/tick, so its sound is a soft fox-sleep default.
        posePort = new BukkitPacketPosePort(server, scheduler, mock(NpcPackets.class), new NoopLogger(), 1, 30f);
        snores = new BukkitSnores(server, scheduler, new NoopLogger(), "minecraft:entity.fox.sleep", 0.5f, 1.0f, 20);

        startSit =
                new StartSit(sessions, seats, new AllowAllRegionGate(), locator, events, Clock.systemUTC(), true, true);
        startPose = new StartPose(
                sessions,
                seats,
                new AllowAllRegionGate(),
                posePort,
                snores,
                events,
                Clock.systemUTC(),
                true,
                true,
                true,
                true);
        startCrawl = new StartCrawl(
                sessions,
                crawlSessions,
                crawlView,
                posePort,
                new AllowAllRegionGate(),
                events,
                Clock.systemUTC(),
                true);
        playerSitPreferences = new PdcPlayerSitPreferences();
        StartPlayerSit startPlayerSit = new StartPlayerSit(
                sessions,
                seats,
                playerSitPreferences,
                new AllowAllRegionGate(),
                locator,
                events,
                Clock.systemUTC(),
                true);
        togglePlayerSit = new TogglePlayerSit(playerSitPreferences);
        stopPose = new StopPose(
                sessions,
                crawlSessions,
                seats,
                posePort,
                snores,
                crawlView,
                new BukkitPoseReturn(plugin, scheduler),
                events,
                true);
        interactListener = new SeatInteractListener(startSit, seats, sittableBlocks, new KeyMessages(), true, 5.0);
        playerSitInteractListener = new PlayerSitInteractListener(startPlayerSit, new KeyMessages());
        cancelListener = new PoseCancelListener(stopPose, sessions);
        crawlMoveListener = new CrawlMoveListener(sessions, crawlSessions, crawlView);
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
    void layingAnchorsThePlayerOnATaggedSeatAndThePlaceholdersReport() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position feet = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));

        startPose.start(who, PoseType.LAY, feet, feet.yaw());

        List<Entity> tagged = taggedSeats();
        assertThat(tagged).hasSize(1);
        assertThat(tagged.get(0)).isInstanceOf(ArmorStand.class);
        assertThat(tagged.get(0).getPassengers()).contains(player);
        assertThat(sessions.current(who).orElseThrow().type()).isEqualTo(PoseType.LAY);
        // The free-pose placeholders report the live pose; the plain-sit placeholder stays false for a lay.
        assertThat(placeholders.posing(who)).isTrue();
        assertThat(placeholders.pose(who)).isEqualTo("lay");
        assertThat(placeholders.sitting(who)).isFalse();
    }

    @Test
    void spinningAdvancesTheSeatYawAcrossTicksAndStoppingCancelsIt() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position feet = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));

        startPose.start(who, PoseType.SPIN, feet, feet.yaw());
        ArmorStand seat = (ArmorStand) taggedSeats().get(0);
        assertThat(seat.getPassengers()).contains(player);
        assertThat(posePort.isSpinning(player.getUniqueId())).isTrue();

        posePort.tick();
        float afterOne = Objects.requireNonNull(seat.getLocation(), "location").getYaw();
        posePort.tick();
        float afterTwo = Objects.requireNonNull(seat.getLocation(), "location").getYaw();
        // The repeating pass turns the seat a little more each tick, so the yaw strictly advances.
        assertThat(afterTwo).isGreaterThan(afterOne);

        stopPose.stop(who);

        // Stopping cancels the spin (no lingering rotation) and removes the seat (no ghost).
        assertThat(posePort.isSpinning(player.getUniqueId())).isFalse();
        assertThat(taggedSeats()).isEmpty();
        posePort.tick(); // a further pass is a harmless no-op — the player is no longer in the spin set
    }

    @Test
    void snoringStartsOnLayAndStopsOnStop() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position feet = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));

        startPose.start(who, PoseType.LAY, feet, feet.yaw());
        assertThat(snores.isSnoring(player.getUniqueId())).isTrue();
        snores.tick(); // the loop runs without throwing — it plays the snore sound at the laying player

        stopPose.stop(who);
        assertThat(snores.isSnoring(player.getUniqueId())).isFalse();
    }

    @Test
    void sneakingEndsAFreePoseClearsItAndRemovesTheSeat() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position feet = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
        startPose.start(who, PoseType.LAY, feet, feet.yaw());
        assertThat(taggedSeats()).hasSize(1);

        cancelListener.onSneak(new PlayerToggleSneakEvent(player, true));

        assertThat(sessions.isPosing(who)).isFalse();
        assertThat(taggedSeats()).isEmpty(); // no ghost seat left behind
        assertThat(placeholders.posing(who)).isFalse();
        assertThat(snores.isSnoring(player.getUniqueId())).isFalse();
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

    @Test
    void crawlingRecordsACrawlSessionShowsTheFakeBlockAndReportsThePosePlaceholder() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position head = CrawlSessions.headBlockAbove(feetOf(player));

        startCrawl.start(who, feetOf(player));

        // The crawl fakes a block a block above the head and records a CRAWL session — no seat entity is spawned.
        assertThat(crawlView.shown).containsExactly(head);
        assertThat(taggedSeats()).isEmpty();
        assertThat(sessions.current(who).orElseThrow().type()).isEqualTo(PoseType.CRAWL);
        assertThat(crawlSessions.current(who)).contains(head);
        // The pose placeholders read the crawl: posing yes, pose "crawl", and the plain-sit placeholder stays false.
        assertThat(placeholders.posing(who)).isTrue();
        assertThat(placeholders.pose(who)).isEqualTo("crawl");
        assertThat(placeholders.sitting(who)).isFalse();
    }

    @Test
    void movingToANewBlockRestoresTheOldHeadAndShowsTheNewOne() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position oldHead = CrawlSessions.headBlockAbove(feetOf(player));
        startCrawl.start(who, feetOf(player));

        Location from = Objects.requireNonNull(player.getLocation(), "location");
        Location to = new Location(world, 1.5, 64, 0.5);
        Position newHead = CrawlSessions.headBlockAbove(BukkitRefs.toPosition(to));
        crawlMoveListener.onMove(new PlayerMoveEvent(player, from, to));

        // The fake block follows the player: the block just left is restored, a new one is shown above the new head.
        assertThat(crawlView.restored).containsExactly(oldHead);
        assertThat(crawlView.shown).containsExactly(oldHead, newHead);
        assertThat(crawlSessions.current(who)).contains(newHead);
    }

    @Test
    void aLookOnlyMoveWithinTheSameBlockDoesNotDisturbTheCrawl() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        startCrawl.start(who, feetOf(player));
        crawlView.restored.clear();
        crawlView.shown.clear();

        Location from = Objects.requireNonNull(player.getLocation(), "location");
        Location to = new Location(world, 0.6, 64, 0.6, 45f, 10f); // same block, only a small turn/step
        crawlMoveListener.onMove(new PlayerMoveEvent(player, from, to));

        // No block boundary crossed, so nothing is restored or re-faked — the hot path skips the follow work.
        assertThat(crawlView.restored).isEmpty();
        assertThat(crawlView.shown).isEmpty();
    }

    @Test
    void aSecondCrawlRestoresTheRealBlockAndClearsTheSession() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position head = CrawlSessions.headBlockAbove(feetOf(player));
        startCrawl.start(who, feetOf(player));

        // A second /crawl toggles off through StopPose (the command's toggle-off path).
        stopPose.stop(who);

        assertThat(crawlView.restored).containsExactly(head);
        assertThat(sessions.isPosing(who)).isFalse();
        assertThat(crawlSessions.current(who)).isEmpty();
    }

    @Test
    void quittingWhileCrawlingRestoresTheBlockAndLeavesNoLingeringFake() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position head = CrawlSessions.headBlockAbove(feetOf(player));
        startCrawl.start(who, feetOf(player));

        cancelListener.onQuit(
                new PlayerQuitEvent(player, Component.text("bye"), PlayerQuitEvent.QuitReason.DISCONNECTED));

        // The phantom-block-prevention proof: the real block is restored at the last fake position and no crawl
        // state lingers, so the leaver's client is never left with a stranded fake block.
        assertThat(crawlView.restored).containsExactly(head);
        assertThat(sessions.isPosing(who)).isFalse();
        assertThat(crawlSessions.current(who)).isEmpty();
    }

    @Test
    void teleportingWhileCrawlingRestoresTheBlockAndClearsTheSession() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        Position head = CrawlSessions.headBlockAbove(feetOf(player));
        startCrawl.start(who, feetOf(player));

        cancelListener.onTeleport(
                new PlayerTeleportEvent(player, new Location(world, 0.5, 64, 0.5), new Location(world, 40, 70, 40)));

        assertThat(crawlView.restored).containsExactly(head);
        assertThat(sessions.isPosing(who)).isFalse();
        assertThat(crawlSessions.current(who)).isEmpty();
    }

    @Test
    void sneakingWhileCrawlingDoesNotEndItUnlikeASit() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerRef who = BukkitRefs.toRef(player);
        startCrawl.start(who, feetOf(player));

        cancelListener.onSneak(new PlayerToggleSneakEvent(player, true));

        // A crawler actively moves and may sneak without meaning to stand up, so sneak leaves the crawl running —
        // the contrast with a sit, which ends on sneak (sneakingEndsThePoseAndReturnsThePlayerToWhereTheySat).
        assertThat(sessions.current(who).orElseThrow().type()).isEqualTo(PoseType.CRAWL);
        assertThat(crawlView.restored).isEmpty();
    }

    @Test
    void takingDamageWhileCrawlingDoesNotEndIt() {
        PlayerMock player = playerAt(0.5, 64, 0.5);
        PlayerMock attacker = server.addPlayer("Attacker");
        PlayerRef who = BukkitRefs.toRef(player);
        startCrawl.start(who, feetOf(player));

        EntityDamageEvent damage = player.simulateDamage(1.0, attacker);
        cancelListener.onDamage(damage);

        assertThat(sessions.current(who).orElseThrow().type()).isEqualTo(PoseType.CRAWL);
        assertThat(crawlView.restored).isEmpty();
    }

    private static Position feetOf(PlayerMock player) {
        return BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            // The spin and snore loops are driven deterministically by the tests (via posePort.tick() /
            // snores.tick()), so the repeating registration is a no-op that hands back a closeable to cancel.
            return () -> {};
        }
    }

    /**
     * Records the head positions the crawl was asked to fake and to restore. Routed through the port so the test
     * never touches {@code Player.sendBlockChange}, which MockBukkit does not implement — the recorded show/restore
     * calls are the phantom-block-prevention probe.
     */
    private static final class RecordingCrawlView implements CrawlView {
        private final List<Position> shown = new ArrayList<>();
        private final List<Position> restored = new ArrayList<>();

        @Override
        public void showFakeBlockAbove(PlayerRef who, Position headBlock) {
            shown.add(headBlock);
        }

        @Override
        public void restoreRealBlock(PlayerRef who, Position headBlock) {
            restored.add(headBlock);
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
