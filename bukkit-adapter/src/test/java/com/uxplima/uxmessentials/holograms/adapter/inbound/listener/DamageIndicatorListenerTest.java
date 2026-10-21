package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.holograms.adapter.outbound.DamageIndicatorConfig;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.packet.display.FakeHologramPackets;
import com.uxplima.uxmlib.packet.display.FakeRegionScheduler;
import com.uxplima.uxmlib.packet.display.HologramProbe;
import com.uxplima.uxmlib.packet.display.PacketLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A floating number is sent to the players near the hurt or healed entity and taken back after the configured time.
 * The world holds no entity for it, so a crash or a chunk unload cannot leave one behind.
 */
class DamageIndicatorListenerTest {

    private ServerMock server;
    private World world;
    private final FakeHologramPackets packets = new FakeHologramPackets();
    private final FakeRegionScheduler frames = new FakeRegionScheduler();
    private final Later later = new Later();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aHealIsSentToThePlayersNearAndTakenBackAfterItsTime() {
        PlayerMock watcher = server.addPlayer();
        watcher.teleport(new Location(world, 2, 64, 2));
        Zombie zombie = (Zombie) world.spawnEntity(new Location(world, 0, 64, 0), org.bukkit.entity.EntityType.ZOMBIE);
        DamageIndicatorListener listener = new DamageIndicatorListener(
                new DamageIndicatorConfig(true, true, true, true, 20, "-{amount}", "-{amount}!", "+{amount}"),
                later,
                frames,
                packets);

        listener.onHeal(new EntityRegainHealthEvent(zombie, 2.0, EntityRegainHealthEvent.RegainReason.MAGIC));
        frames.firstFrame();
        List<PacketLine> shown = new HologramProbe(packets).heldBy(watcher);

        assertThat(shown).hasSize(1);
        assertThat(PlainTextComponentSerializer.plainText().serialize(((PacketLine.Text) shown.get(0)).text()))
                .isEqualTo("+2");
        assertThat(world.getEntitiesByClass(TextDisplay.class)).isEmpty();
        assertThat(later.delays).containsExactly(Duration.ofSeconds(1));

        later.runAll();
        assertThat(new HologramProbe(packets).heldBy(watcher)).isEmpty();
    }

    /** Runs a region task at once and holds a delayed one until the test says. */
    private static final class Later implements Scheduler {

        private final List<Duration> delays = new ArrayList<>();
        private final List<Runnable> held = new ArrayList<>();

        void runAll() {
            List<Runnable> due = List.copyOf(held);
            held.clear();
            due.forEach(Runnable::run);
        }

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
            delays.add(delay);
            held.add(task);
        }
    }
}
