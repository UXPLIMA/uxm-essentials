package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.inventory.InventoryHolder;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.custommenus.adapter.CustomMenuLoader;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.MenuCommand;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
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
 * MockBukkit coverage of the operator {@code /menu} command through its real Brigadier node. A spec is registered
 * under {@code shop}; dispatching {@code /menu open shop} must land the viewer in a {@link MenuHolder}-backed
 * window, an unknown name replies with the not-found line and crashes nothing, {@code /menu list} surfaces the
 * registered names, and {@code /menu reload} re-runs the loader and reports the counts.
 */
class MenuCommandTest {

    private static final String SPEC_HOCON = """
            rows = 1
            items { x { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
            """;

    private ServerMock server;
    private PlayerMock player;
    private Menus menus;
    private RecordingMessages messages;
    private final List<String> names = new ArrayList<>();
    private final AtomicInteger reloads = new AtomicInteger();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Operator");
        player.setOp(true); // the happy-path dispatches hold both the use and admin nodes; a gate test drops them
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        menus = new Menus(renderer, new SyncScheduler(), new ListSourceRegistry());
        MenuSpec spec = new MenuSpecLoader().parse(SPEC_HOCON);
        menus.registerSpec("shop", spec);
        names.add("shop");
        messages = new RecordingMessages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openShowsTheRegisteredSpecInAMenuHolderWindow() {
        execute("menu open shop", player);

        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        assertThat(holder).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) holder).specId()).isEqualTo("shop");
    }

    @Test
    void openUnknownNameRepliesNotFoundWithoutCrashing() {
        execute("menu open ghost", player);

        assertThat(menuHolderIsOpenFor(player)).isFalse();
        assertThat(messages.keys).contains("menu.not-found");
    }

    @Test
    void openForOtherOpensTheMenuForTheNamedTarget() {
        PlayerMock steve = server.addPlayer("Steve");

        execute("menu open shop Steve", player);

        var top = steve.getOpenInventory().getTopInventory();
        assertThat(top.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) top.getHolder()).specId()).isEqualTo("shop");
        // The invoking operator opens it for the target, not for themselves.
        assertThat(menuHolderIsOpenFor(player)).isFalse();
        assertThat(messages.keys).contains("menu.opened-for");
    }

    @Test
    void openForOtherIsHiddenWithoutTheOthersNodeButSelfOpenStillWorks() {
        PlayerMock plain = server.addPlayer("Plain"); // holds use but not the open.others node
        plain.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.menu.use", true);
        server.addPlayer("Steve");

        // The target branch is invisible without the others node, so open-for-other fails to parse.
        executeExpectingDenial("menu open shop Steve", plain);
        // But opening the menu for oneself still works.
        execute("menu open shop", plain);

        assertThat(menuHolderIsOpenFor(plain)).isTrue();
    }

    @Test
    void openForOtherWithAnUnknownMenuNameRepliesNotFound() {
        PlayerMock steve = server.addPlayer("Steve");

        execute("menu open ghost Steve", player);

        assertThat(messages.keys).contains("menu.not-found");
        assertThat(menuHolderIsOpenFor(steve)).isFalse();
    }

    @Test
    void listSurfacesTheRegisteredNames() {
        execute("menu list", player);

        assertThat(messages.keys).contains("menu.list.header", "menu.list.entry");
        assertThat(messages.placeholdersFor("menu.list.entry")).containsEntry("name", "shop");
    }

    @Test
    void listOnAnEmptyRegistrySaysSo() {
        names.clear();

        execute("menu list", player);

        assertThat(messages.keys).contains("menu.list.empty");
    }

    @Test
    void reloadReRunsTheLoaderAndReportsCounts() {
        execute("menu reload", player);

        assertThat(reloads.get()).isEqualTo(1);
        assertThat(messages.keys).contains("menu.reloaded");
    }

    @Test
    void openFromConsoleRepliesPlayersOnly() {
        execute("menu open shop", server.getConsoleSender());

        assertThat(messages.keys).contains("command.players-only");
    }

    @Test
    void openIsGatedByTheUsePermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // not op, so the requires gate hides the open branch
        executeExpectingDenial("menu open shop", plain);

        assertThat(menuHolderIsOpenFor(plain)).isFalse();
    }

    @Test
    void reloadIsGatedByTheAdminPermission() {
        PlayerMock plain = server.addPlayer("NoPerms"); // not op, so the admin-gated reload branch stays hidden
        executeExpectingDenial("menu reload", plain);

        assertThat(reloads.get()).isZero();
    }

    private void execute(String input, org.bukkit.command.CommandSender who) {
        try {
            dispatcherFor().execute(input, CommandSourceStackMock.from(who));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    /**
     * Dispatch when the sender is expected to lack the gating node: Brigadier hides a {@code requires}-gated branch
     * from a sender without the permission, so the input fails to parse — that parse failure is the denial we assert.
     */
    private void executeExpectingDenial(String input, org.bukkit.command.CommandSender who) {
        try {
            dispatcherFor().execute(input, CommandSourceStackMock.from(who));
            throw new AssertionError("expected a permission denial for: " + input);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
            // The gated branch is invisible to this sender, so the dispatch is rejected — the denial we wanted.
        }
    }

    private CommandDispatcher<CommandSourceStack> dispatcherFor() {
        MenuCommand command = new MenuCommand(
                menus,
                () -> List.copyOf(names),
                () -> {
                    reloads.incrementAndGet();
                    return new CustomMenuLoader.LoadResult(List.of("shop", "spawn"), List.of("broken"));
                },
                messages);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        return dispatcher;
    }

    /** True when {@code who}'s open top inventory is one of the menu engine's holder-backed windows. */
    private static boolean menuHolderIsOpenFor(PlayerMock who) {
        var top = who.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    /**
     * Records each delivered key and the placeholders it carried so a path's outcome is asserted by the line it
     * produced. {@link CommandFeedback} re-resolves the {@code prefix} key (with empty placeholders) on every send,
     * so the per-key map keeps the content key's own placeholders rather than being clobbered by that prefix pass.
     */
    private static final class RecordingMessages implements Messages {
        private final List<String> keys = new ArrayList<>();
        private final Map<String, Map<String, String>> byKey = new java.util.HashMap<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key.key());
            byKey.put(key.key(), placeholders);
            return key.key();
        }

        Map<String, String> placeholdersFor(String key) {
            return byKey.getOrDefault(key, Map.of());
        }
    }

    /** A pass-through messages double for the GuiText the renderer leans on (titles/names resolve to their key). */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

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
        public void asyncAfter(java.time.Duration delay, Runnable task) {
            task.run();
        }
    }
}
