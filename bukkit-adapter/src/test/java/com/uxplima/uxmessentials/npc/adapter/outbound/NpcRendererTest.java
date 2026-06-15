package com.uxplima.uxmessentials.npc.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the {@link NpcRenderer} dispatch under MockBukkit with a fake {@link com.uxplima.uxmlib.packet.npc.NpcPackets}:
 * a render to an in-range viewer sends one spawn bundle (tab-add + spawn) and schedules the tab-remove that hides the
 * entry; the renderer tracks which NPCs each viewer has been shown; a delete and a forget-then-quit both leave no ghost;
 * and an out-of-range viewer is never spawned (and is removed when it falls out of range).
 */
class NpcRendererTest {

    private ServerMock server;
    private RecordingNpcPackets packets;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        packets = new RecordingNpcPackets();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rendersOneSpawnBundleAndSchedulesTheTabHideForAnInRangeViewer() {
        PlayerMock viewer = server.addPlayer();
        InlineScheduler scheduler = new InlineScheduler();
        NpcRenderer renderer = new NpcRenderer(packets, scheduler, 48.0, 16.0, Duration.ofSeconds(1));

        renderer.render(npcAt(viewer, 1.0)); // one block away — in range

        // Exactly one bundle (tab-add + spawn) reached the viewer, plus the two look packets and the deferred
        // tab-remove.
        assertThat(packets.bundlesSentTo(viewer.getUniqueId())).hasSize(1);
        assertThat(packets.bundles).hasSize(1);
        assertThat(packets.bundles.get(0)).hasSize(2); // tab-add + spawn travel together
        assertThat(packets.tabAdds).hasSize(1);
        assertThat(packets.spawns).hasSize(1);
        // The tab entry is hidden a moment later so the NPC keeps its skin but never shows in the tab list.
        assertThat(packets.tabRemovesSentTo(viewer.getUniqueId())).hasSize(1);
    }

    @Test
    void linksTheSpawnUuidToTheTabAddUuidSoTheSkinResolves() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = new NpcRenderer(packets, new InlineScheduler(), 48.0, 16.0, Duration.ofSeconds(1));

        renderer.render(npcAt(viewer, 1.0));

        UUID expected = RenderedNpc.profileIdFor("guide");
        assertThat(packets.tabAdds.get(0).profileId()).isEqualTo(expected);
        assertThat(packets.spawns.get(0).profileId()).isEqualTo(expected);
        // The spawn's profile uuid must equal the tab-add's, or the client won't attach the skin to the entity.
        assertThat(packets.spawns.get(0).profileId())
                .isEqualTo(packets.tabAdds.get(0).profileId());
    }

    @Test
    void carriesTheStoredSkinOnTheTabAdd() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = new NpcRenderer(packets, new InlineScheduler(), 48.0, 16.0, Duration.ofSeconds(1));

        renderer.render(npc(locationOf(viewer), new NpcSkin("tex", "sig")));

        TabSkin skin = java.util.Objects.requireNonNull(packets.tabAdds.get(0).skin(), "tab skin");
        assertThat(skin.textureValue()).isEqualTo("tex");
        assertThat(skin.signature()).isEqualTo("sig");
    }

    @Test
    void doesNotSpawnAnOutOfRangeViewer() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = new NpcRenderer(packets, new InlineScheduler(), 48.0, 16.0, Duration.ofSeconds(1));

        renderer.render(npcAt(viewer, 100.0)); // far away — out of range

        assertThat(packets.spawns).isEmpty();
        assertThat(packets.bundlesSentTo(viewer.getUniqueId())).isEmpty();
    }

    @Test
    void despawnRemovesTheNpcFromEveryViewerThatHadIt() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = new NpcRenderer(packets, new InlineScheduler(), 48.0, 16.0, Duration.ofSeconds(1));
        renderer.render(npcAt(viewer, 1.0));

        renderer.despawn(NpcName.of("guide"));

        // The fake player is removed from the viewer (entity-remove + tab-remove); no ghost is left behind.
        assertThat(packets.removes).hasSize(1);
        assertThat(packets.removesSentTo(viewer.getUniqueId())).hasSize(1);
    }

    @Test
    void forgetDropsTheViewerWithoutAnyRemovePacket() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = new NpcRenderer(packets, new InlineScheduler(), 48.0, 16.0, Duration.ofSeconds(1));
        renderer.render(npcAt(viewer, 1.0));
        int sendsBefore = packets.removes.size();

        // On quit the channel is closing, so forget only drops the tracking — no remove packet is sent to a dead
        // client.
        renderer.forget(viewer);

        assertThat(packets.removes).hasSize(sendsBefore);
        // A re-render after forget shows the NPC again (the tracking was cleared, so it is not an unchanged no-op).
        renderer.render(npcAt(viewer, 1.0));
        assertThat(packets.spawns).hasSize(2);
    }

    @Test
    void removesAnNpcThatMovedOutOfRangeOfAViewer() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = new NpcRenderer(packets, new InlineScheduler(), 48.0, 16.0, Duration.ofSeconds(1));
        renderer.render(npcAt(viewer, 1.0)); // in range -> shown

        Npc moved = npcAt(viewer, 100.0); // same name, now far away
        renderer.render(moved);

        // The move out of range removes the previously-shown fake player from the viewer.
        assertThat(packets.removesSentTo(viewer.getUniqueId())).hasSize(1);
    }

    @Test
    void despawnAllRemovesEveryNpcFromEveryViewerAndClearsTracking() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = new NpcRenderer(packets, new InlineScheduler(), 48.0, 16.0, Duration.ofSeconds(1));
        renderer.render(npcAt(viewer, 1.0));

        renderer.despawnAll();

        assertThat(packets.removesSentTo(viewer.getUniqueId())).hasSize(1);
        // After a full despawn a refresh re-spawns nothing (the live set was cleared).
        assertThatCode(renderer::refresh).doesNotThrowAnyException();
        assertThat(packets.spawns).hasSize(1);
    }

    @Test
    void schedulesButDoesNotImmediatelySendTheTabRemoveWhenTheHopIsDeferred() {
        PlayerMock viewer = server.addPlayer();
        DeferredScheduler scheduler = new DeferredScheduler();
        NpcRenderer renderer = new NpcRenderer(packets, scheduler, 48.0, 16.0, Duration.ofSeconds(1));

        renderer.render(npcAt(viewer, 1.0));

        // The spawn bundle has gone out, but the tab-remove is queued for later, not sent yet.
        assertThat(packets.bundlesSentTo(viewer.getUniqueId())).hasSize(1);
        assertThat(packets.tabRemoves).isEmpty();
        assertThat(scheduler.pendingAsyncAfter).hasSize(1);

        scheduler.runAllAsyncAfter();
        assertThat(packets.tabRemovesSentTo(viewer.getUniqueId())).hasSize(1);
    }

    @Test
    void lookTickAimsAnInRangeViewerOfALookingNpc() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = new NpcRenderer(packets, new InlineScheduler(), 48.0, 16.0, Duration.ofSeconds(1));
        renderer.render(npcAt(viewer, 2.0)); // shown, and within the 16-block look range
        int looksBefore = packets.looksSentTo(viewer.getUniqueId()).size();

        renderer.lookTick();

        // The look tick re-aims the NPC at this viewer: a head-look and a body-look on top of the spawn pair.
        assertThat(packets.looksSentTo(viewer.getUniqueId())).hasSize(looksBefore + 2);
    }

    @Test
    void lookTickIgnoresAnNpcWithLookAtPlayerOff() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = new NpcRenderer(packets, new InlineScheduler(), 48.0, 16.0, Duration.ofSeconds(1));
        renderer.render(npcAt(viewer, 2.0).withLookAtPlayer(false));
        int looksBefore = packets.looksSentTo(viewer.getUniqueId()).size();

        renderer.lookTick();

        assertThat(packets.looksSentTo(viewer.getUniqueId())).hasSize(looksBefore);
    }

    @Test
    void lookTickIgnoresAViewerBeyondTheLookRange() {
        PlayerMock viewer = server.addPlayer();
        NpcRenderer renderer = new NpcRenderer(packets, new InlineScheduler(), 48.0, 16.0, Duration.ofSeconds(1));
        renderer.render(npcAt(viewer, 30.0)); // shown (render range 48) but past the 16-block look range
        int looksBefore = packets.looksSentTo(viewer.getUniqueId()).size();

        renderer.lookTick();

        assertThat(packets.looksSentTo(viewer.getUniqueId())).hasSize(looksBefore);
    }

    private Npc npcAt(Player viewer, double offset) {
        return npc(locationOf(viewer).add(offset, 0, 0), null);
    }

    private static org.bukkit.Location locationOf(Player viewer) {
        return java.util.Objects.requireNonNull(viewer.getLocation(), "viewer location")
                .clone();
    }

    private Npc npc(org.bukkit.Location at, @Nullable NpcSkin skin) {
        // Build the position from the live Bukkit location (same world uid as the viewer) so the renderer's
        // same-world range check resolves a real distance rather than treating the worlds as infinitely apart.
        Position position = com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs.toPosition(at);
        return Npc.create(NpcName.of("guide"), position, skin, Instant.ofEpochMilli(1_000));
    }

    /** A fake {@link com.uxplima.uxmlib.packet.npc.NpcPackets} recording each built packet and the per-viewer sends. */
    private static final class RecordingNpcPackets implements com.uxplima.uxmlib.packet.npc.NpcPackets {
        private final AtomicInteger nextId = new AtomicInteger(1);
        private final List<TabAdd> tabAdds = new ArrayList<>();
        private final List<Spawn> spawns = new ArrayList<>();
        private final List<UUID> tabRemoves = new ArrayList<>();
        private final List<Integer> removes = new ArrayList<>();
        private final List<List<Object>> bundles = new ArrayList<>();
        private final List<Sent> sent = new ArrayList<>();

        @Override
        public int allocateEntityId() {
            return nextId.getAndIncrement();
        }

        @Override
        public Object tabAdd(UUID profileId, String name, @Nullable TabSkin skin) {
            TabAdd packet = new TabAdd(profileId, name, skin);
            tabAdds.add(packet);
            return packet;
        }

        @Override
        public Object tabRemove(UUID profileId) {
            return new TabRemove(profileId);
        }

        @Override
        public Object spawnPlayer(int entityId, UUID profileId, double x, double y, double z, float yaw, float pitch) {
            Spawn packet = new Spawn(entityId, profileId);
            spawns.add(packet);
            return packet;
        }

        @Override
        public Object headLook(int entityId, float yaw) {
            return new Look(entityId, yaw, 0f);
        }

        @Override
        public Object bodyLook(int entityId, float yaw, float pitch) {
            return new Look(entityId, yaw, pitch);
        }

        @Override
        public Object teleport(int entityId, double x, double y, double z, float yaw, float pitch) {
            return new Look(entityId, yaw, pitch);
        }

        @Override
        public Object remove(int entityId) {
            return new Remove(entityId);
        }

        @Override
        public Object bundle(List<Object> built) {
            List<Object> copy = List.copyOf(built);
            bundles.add(copy);
            return new Bundle(copy);
        }

        @Override
        public void send(Player viewer, Object packet) {
            sent.add(new Sent(viewer.getUniqueId(), packet));
            record(packet);
        }

        private void record(Object packet) {
            if (packet instanceof TabRemove tabRemove) {
                tabRemoves.add(tabRemove.profileId());
            } else if (packet instanceof Remove remove) {
                removes.add(remove.entityId());
            }
        }

        List<Bundle> bundlesSentTo(UUID viewer) {
            return sentTo(viewer, Bundle.class);
        }

        List<Look> looksSentTo(UUID viewer) {
            return sentTo(viewer, Look.class);
        }

        List<TabRemove> tabRemovesSentTo(UUID viewer) {
            return sentTo(viewer, TabRemove.class);
        }

        List<Remove> removesSentTo(UUID viewer) {
            return sentTo(viewer, Remove.class);
        }

        private <T> List<T> sentTo(UUID viewer, Class<T> type) {
            List<T> matches = new ArrayList<>();
            for (Sent s : sent) {
                if (s.viewer().equals(viewer) && type.isInstance(s.packet())) {
                    matches.add(type.cast(s.packet()));
                }
            }
            return matches;
        }

        private record Sent(UUID viewer, Object packet) {}

        private record TabAdd(UUID profileId, String name, @Nullable TabSkin skin) {}

        private record TabRemove(UUID profileId) {}

        private record Spawn(int entityId, UUID profileId) {}

        private record Look(int entityId, float yaw, float pitch) {}

        private record Remove(int entityId) {}

        private record Bundle(List<Object> packets) {}
    }

    /** A scheduler that runs every hop inline so the spawn and the deferred tab-hide both complete immediately. */
    private static class InlineScheduler implements Scheduler {
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

    /** A scheduler that runs entity hops inline but queues the {@code asyncAfter} tab-hide so a test can run it later. */
    private static final class DeferredScheduler extends InlineScheduler {
        private final List<Runnable> pendingAsyncAfter = new ArrayList<>();

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            pendingAsyncAfter.add(task);
        }

        void runAllAsyncAfter() {
            List<Runnable> snapshot = List.copyOf(pendingAsyncAfter);
            pendingAsyncAfter.clear();
            snapshot.forEach(Runnable::run);
        }
    }
}
