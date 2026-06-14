package com.uxplima.uxmessentials.vote.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.bukkit.Sound;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vote.application.port.BroadcastVisibility;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link BukkitVoteBroadcaster}: a CHAT broadcast reaches a receiving recipient, an
 * opted-out recipient is skipped, and the ACTION_BAR/TITLE/SUBTITLE/BOSS_BAR channels plus an unknown sound
 * are tolerated without throwing. The scheduler runs inline so the entity-thread and async-after hops
 * execute synchronously inside the test.
 */
class BukkitVoteBroadcasterTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final MessageKey THANKS = () -> "vote.thanks";

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void chatBroadcastReachesAReceivingRecipient() {
        VoteBroadcaster broadcaster = broadcaster(new AlwaysReceives(), display(null));

        broadcaster.broadcast(THANKS, Map.of("player", "Alice"), Set.of(BroadcastChannel.CHAT));

        // The fake Messages echoes the key, so the recipient receives the rendered key text.
        assertThat(PLAIN.serialize(player.nextComponentMessage())).contains("vote.thanks");
    }

    @Test
    void anOptedOutRecipientIsSkipped() {
        VoteBroadcaster broadcaster = broadcaster(new NeverReceives(), display(null));

        broadcaster.broadcast(THANKS, Map.of("player", "Alice"), Set.of(BroadcastChannel.CHAT));

        // No chat message is delivered to a player who hid broadcasts.
        assertThat(player.nextComponentMessage()).isNull();
    }

    @Test
    void emptyChannelSetIsANoOp() {
        VoteBroadcaster broadcaster = broadcaster(new AlwaysReceives(), display(null));

        broadcaster.broadcast(THANKS, Map.of(), Set.of());

        assertThat(player.nextComponentMessage()).isNull();
    }

    @Test
    void actionBarTitleSubtitleAndBossBarChannelsDoNotThrow() {
        Set<BroadcastChannel> all = Set.of(
                BroadcastChannel.ACTION_BAR,
                BroadcastChannel.TITLE,
                BroadcastChannel.SUBTITLE,
                BroadcastChannel.BOSS_BAR);
        VoteBroadcaster broadcaster = broadcaster(new AlwaysReceives(), display(null));

        assertThatCode(() -> broadcaster.broadcast(THANKS, Map.of("player", "Alice"), all))
                .doesNotThrowAnyException();
    }

    @Test
    void anUnknownSoundIsToleratedWithoutThrowing() {
        // BukkitRegistryKeys returns null for an unknown name; the broadcaster must skip it, not throw.
        @Nullable Sound unknown = BukkitRegistryKeys.resolveSound("definitely_not_a_real_sound_xyz");
        VoteBroadcaster broadcaster = broadcaster(new AlwaysReceives(), display(unknown));

        assertThatCode(() -> broadcaster.broadcast(THANKS, Map.of("player", "Alice"), Set.of(BroadcastChannel.CHAT)))
                .doesNotThrowAnyException();
    }

    private VoteBroadcaster broadcaster(
            BroadcastVisibility visibility, BukkitVoteBroadcaster.BroadcastDisplay display) {
        return new BukkitVoteBroadcaster(new SyncScheduler(), new EchoMessages(), visibility, display);
    }

    private static BukkitVoteBroadcaster.BroadcastDisplay display(@Nullable Sound sound) {
        return new BukkitVoteBroadcaster.BroadcastDisplay(
                100, 500, 100, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS, 1, sound);
    }

    /** A {@link Messages} fake that resolves a key to its own key string (no catalog needed in the test). */
    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class AlwaysReceives implements BroadcastVisibility {
        @Override
        public boolean receivesBroadcasts(PlayerRef who) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }
    }

    private static final class NeverReceives implements BroadcastVisibility {
        @Override
        public boolean receivesBroadcasts(PlayerRef who) {
            return false;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return false;
        }
    }

    /** Runs every hop inline so the entity-thread and async-after work executes within the test. */
    private static final class SyncScheduler implements Scheduler {
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
}
