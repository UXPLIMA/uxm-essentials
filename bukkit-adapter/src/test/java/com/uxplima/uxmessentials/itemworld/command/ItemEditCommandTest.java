package com.uxplima.uxmessentials.itemworld.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.command.ItemworldGroupACommands;
import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.application.port.ItemworldAudit;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
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
 * MockBukkit coverage of {@code /itemedit} through the real Brigadier nodes: renaming strips the default italic
 * and sets the display name, {@code resetname} clears it, and every lore op (add/set/insert/remove/clear) mutates
 * the held item's lore list exactly as {@link com.uxplima.uxmessentials.itemworld.domain.LorePolicy} dictates —
 * including the out-of-range replies. The no-item, no-permission and config-disabled paths each answer with their
 * message and mutate nothing.
 *
 * <p>The scheduler is a synchronous double so the entity-bound writes are observable without ticking Folia, the
 * permission port is toggleable per test, and the message sink records which {@link MessageKey} each path delivered.
 */
class ItemEditCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private PlayerMock player;
    private RecordingSink sink;
    private MutableConfig config;
    private MutablePermissions permissions;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        sink = new RecordingSink();
        config = new MutableConfig();
        permissions = new MutablePermissions();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void renameSetsTheDisplayNameWithTheDefaultItalicStripped() {
        holdSword();
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "itemedit rename Cool Sword");

        assertThat(sink.keys).contains(ItemworldMessageKey.ITEMEDIT_NAME_SET);
        Component name = heldMeta().displayName();
        assertThat(name).isNotNull();
        assertThat(PLAIN.serialize(name)).isEqualTo("Cool Sword");
        assertThat(name.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
    }

    @Test
    void resetNameClearsTheDisplayName() {
        holdSword();
        CommandDispatcher<CommandSourceStack> dispatcher = register();
        execute(dispatcher, "itemedit rename Named");
        assertThat(heldMeta().displayName()).isNotNull();

        sink.keys.clear();
        execute(dispatcher, "itemedit resetname");

        assertThat(sink.keys).contains(ItemworldMessageKey.ITEMEDIT_NAME_RESET);
        assertThat(heldMeta().hasDisplayName()).isFalse();
    }

    @Test
    void loreAddAppendsALine() {
        holdSword();
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "itemedit lore add first");
        execute(dispatcher, "itemedit lore add second");

        assertThat(sink.keys).contains(ItemworldMessageKey.ITEMEDIT_LORE_ADDED);
        assertThat(plainLore()).containsExactly("first", "second");
    }

    @Test
    void loreSetReplacesTheLineAndRejectsAnOutOfRangeIndex() {
        holdSword();
        CommandDispatcher<CommandSourceStack> dispatcher = register();
        execute(dispatcher, "itemedit lore add a");
        execute(dispatcher, "itemedit lore add b");

        execute(dispatcher, "itemedit lore set 2 replaced");
        assertThat(sink.keys).contains(ItemworldMessageKey.ITEMEDIT_LORE_SET);
        assertThat(plainLore()).containsExactly("a", "replaced");

        sink.keys.clear();
        execute(dispatcher, "itemedit lore set 5 nope");
        assertThat(sink.keys).containsExactly(ItemworldMessageKey.ITEMEDIT_LORE_OUT_OF_RANGE);
        assertThat(plainLore()).containsExactly("a", "replaced"); // unchanged
    }

    @Test
    void loreInsertShiftsTheRestDown() {
        holdSword();
        CommandDispatcher<CommandSourceStack> dispatcher = register();
        execute(dispatcher, "itemedit lore add a");
        execute(dispatcher, "itemedit lore add b");

        execute(dispatcher, "itemedit lore insert 1 top");

        assertThat(sink.keys).contains(ItemworldMessageKey.ITEMEDIT_LORE_INSERTED);
        assertThat(plainLore()).containsExactly("top", "a", "b");
    }

    @Test
    void loreRemoveDropsTheLineAndRejectsAnOutOfRangeIndex() {
        holdSword();
        CommandDispatcher<CommandSourceStack> dispatcher = register();
        execute(dispatcher, "itemedit lore add a");
        execute(dispatcher, "itemedit lore add b");

        execute(dispatcher, "itemedit lore remove 1");
        assertThat(sink.keys).contains(ItemworldMessageKey.ITEMEDIT_LORE_REMOVED);
        assertThat(plainLore()).containsExactly("b");

        sink.keys.clear();
        execute(dispatcher, "itemedit lore remove 9");
        assertThat(sink.keys).containsExactly(ItemworldMessageKey.ITEMEDIT_LORE_OUT_OF_RANGE);
        assertThat(plainLore()).containsExactly("b"); // unchanged
    }

    @Test
    void loreClearRemovesAllLore() {
        holdSword();
        CommandDispatcher<CommandSourceStack> dispatcher = register();
        execute(dispatcher, "itemedit lore add a");
        execute(dispatcher, "itemedit lore add b");

        execute(dispatcher, "itemedit lore clear");

        assertThat(sink.keys).contains(ItemworldMessageKey.ITEMEDIT_LORE_CLEARED);
        assertThat(heldMeta().hasLore()).isFalse();
    }

    @Test
    void anEmptyHandIsRejected() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(); // no item held

        execute(dispatcher, "itemedit rename Whatever");

        assertThat(sink.keys).containsExactly(ItemworldMessageKey.NO_ITEM_IN_HAND);
    }

    @Test
    void aDeniedActorIsRefusedWithAMessageAndNothingMutates() {
        holdSword();
        permissions.deny("uxmessentials.itemworld.itemedit");
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "itemedit rename Denied");

        assertThat(sink.keys).containsExactly(SharedMessageKey.COMMAND_NO_PERMISSION);
        assertThat(heldMeta().hasDisplayName()).isFalse();
    }

    @Test
    void disablingItemEditMakesTheCommandInert() {
        holdSword();
        config.put("item-edit.enabled", false);
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "itemedit lore add nope");

        assertThat(sink.keys).containsExactly(ItemworldMessageKey.COMMAND_DISABLED);
        assertThat(heldMeta().hasLore()).isFalse();
    }

    private void holdSword() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
    }

    private ItemMeta heldMeta() {
        return player.getInventory().getItemInMainHand().getItemMeta();
    }

    private List<String> plainLore() {
        List<Component> lore = heldMeta().lore();
        if (lore == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(lore.size());
        for (Component line : lore) {
            out.add(PLAIN.serialize(line));
        }
        return out;
    }

    private CommandDispatcher<CommandSourceStack> register() {
        ItemworldServices services = new ItemworldServices(
                kernel(),
                mock(ItemworldAudit.class),
                ItemworldConfig.from(config),
                com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout.storageDefault(6));
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        for (CommandRegistration command : ItemworldGroupACommands.all(services, null)) {
            dispatcher.getRoot().addChild(command.build());
        }
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private KernelPorts kernel() {
        return new KernelPorts(
                new SyncScheduler(),
                permissions,
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

    /** A map-backed {@link ConfigStore} scoped to {@code modules.itemworld} so a test flips the item-edit flag. */
    private static final class MutableConfig implements ConfigStore {
        private final Map<String, Object> values = new HashMap<>();

        void put(String path, Object value) {
            values.put(path, value);
        }

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

    /** Denies a set of nodes; every other node is granted, so a test can revoke exactly the itemedit node. */
    private static final class MutablePermissions implements Permissions {
        private final Set<String> denied = new HashSet<>();

        void deny(String node) {
            denied.add(node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return !denied.contains(node);
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
