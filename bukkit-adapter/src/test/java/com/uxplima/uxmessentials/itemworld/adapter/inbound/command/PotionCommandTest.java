package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.application.port.ItemworldAudit;
import com.uxplima.uxmessentials.itemworld.domain.MobSpec;
import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /potion} through its real Brigadier node. A held potion gains a custom
 * {@link org.bukkit.potion.PotionEffect} of the named type ({@code POTION_EFFECT_ADDED}); an unknown effect
 * name ({@code POTION_UNKNOWN_EFFECT}) or a non-potion / empty hand ({@code NOT_A_POTION}) is rejected and the
 * held item is unchanged. The message sink records which {@link MessageKey} each path delivered.
 */
class PotionCommandTest {

    private ServerMock server;
    private PlayerMock player;
    private RecordingSink sink;
    private MutableConfig config;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        player.setOp(true);
        sink = new RecordingSink();
        config = new MutableConfig();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void literalIsPotion() {
        assertThat(new PotionCommand(services()).build().getLiteral()).isEqualTo("potion");
    }

    @Test
    void heldPotionGetsEffect() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.POTION));

        execute("potion speed 30 1");

        ItemStack hand = player.getInventory().getItemInMainHand();
        assertThat(hand.getType()).isEqualTo(Material.POTION);
        PotionMeta meta = (PotionMeta) hand.getItemMeta();
        assertThat(meta.getCustomEffects()).anySatisfy(effect -> {
            assertThat(effect.getType()).isEqualTo(PotionEffectType.SPEED);
            assertThat(effect.getDuration()).isEqualTo(600);
            assertThat(effect.getAmplifier()).isEqualTo(1);
        });
        assertThat(sink.keys).contains(ItemworldMessageKey.POTION_EFFECT_ADDED);
    }

    @Test
    void unknownEffectRejected() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.POTION));

        execute("potion bogus 10 1");

        assertThat(sink.keys).contains(ItemworldMessageKey.POTION_UNKNOWN_EFFECT);
        PotionMeta meta = (PotionMeta) player.getInventory().getItemInMainHand().getItemMeta();
        assertThat(meta.getCustomEffects()).isEmpty();
    }

    @Test
    void nonPotionRejected() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));

        execute("potion speed 10 1");

        assertThat(sink.keys).contains(ItemworldMessageKey.NOT_A_POTION);
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.STONE);
    }

    @Test
    void emptyHandRejected() {
        execute("potion speed 10 1");

        assertThat(sink.keys).contains(ItemworldMessageKey.NOT_A_POTION);
    }

    private void execute(String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new PotionCommand(services()).build());
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private ItemworldServices services() {
        return new ItemworldServices(kernel(), new NoopAudit(), ItemworldConfig.from(config));
    }

    private KernelPorts kernel() {
        return new KernelPorts(
                new SyncScheduler(),
                new AllowAllPermissions(),
                new NoCooldowns(),
                new NoWarmups(),
                new KeyMessages(),
                sink,
                new NoPlayerLookup(),
                new NoWorldLookup(),
                new NoPlayerLocator(),
                new NoEvents(),
                new NoopLogger());
    }

    /** A map-backed {@link ConfigStore} scoped to {@code modules.itemworld}; defaults keep /potion enabled. */
    private static final class MutableConfig implements ConfigStore {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }

    /** Records each delivered key so a path's outcome is asserted by the message it produced. */
    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // renderedText is the key() string (see KeyMessages); the key list is what tests assert on
        }
    }

    /** Resolves a key to its own string and records it on the sink for assertions. */
    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.keys.add(key);
            return key.key();
        }
    }

    /** Runs scheduled work inline so entity-bound effects are observable without ticking Folia. */
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

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who,
                QuotaFamily family,
                com.uxplima.uxmessentials.shared.domain.@org.jspecify.annotations.Nullable WorldRef world,
                long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class NoCooldowns implements Cooldowns {
        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                check(PlayerRef who, CooldownKind kind) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                checkLabel(PlayerRef who, String label) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class NoWarmups implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onComplete.run();
            return new Warmups.CompletedWarmup(who);
        }
    }

    private static final class NoPlayerLookup implements PlayerLookup {
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

    private static final class NoWorldLookup implements WorldLookup {
        @Override
        public Optional<com.uxplima.uxmessentials.shared.domain.WorldRef> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<com.uxplima.uxmessentials.shared.domain.WorldRef> findByUid(UUID uid) {
            return Optional.empty();
        }
    }

    private static final class NoPlayerLocator implements PlayerLocator {
        @Override
        public Optional<Position> locate(PlayerRef who) {
            return Optional.empty();
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class NoopAudit implements ItemworldAudit {
        @Override
        public void gave(PlayerRef actor, PlayerRef target, String itemKey, int amount) {}

        @Override
        public void spawnedMob(PlayerRef actor, MobSpec spec, int spawned) {}

        @Override
        public void retypedSpawner(PlayerRef actor, String mobType) {}

        @Override
        public void killed(PlayerRef actor, String target) {}

        @Override
        public void butchered(PlayerRef actor, PurgeSelection selection, int removed) {}

        @Override
        public void killedAll(PlayerRef actor, PurgeSelection selection, int removed) {}

        @Override
        public void removed(PlayerRef actor, PurgeSelection selection, int removed) {}

        @Override
        public void struckLightning(PlayerRef actor, Optional<PlayerRef> target) {}

        @Override
        public void launchedFireball(PlayerRef actor) {}

        @Override
        public void firedKittycannon(PlayerRef actor) {}

        @Override
        public void brokeBlock(PlayerRef actor, String blockType) {}

        @Override
        public void grewTree(PlayerRef actor, String type) {}
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
