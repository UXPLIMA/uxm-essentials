package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.RequirementConditions;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.Hooks;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The proof that an item's {@code view} gate is now a full requirement block, not just a flat AND list: it can gate
 * visibility on a minimum (OR / N-of-M) and on an inverted condition, while a plain flat list still means AND exactly
 * as before. The real {@link MenuSpecLoader}, {@link MenuBindings#validate}, {@link MenuRenderer} and {@link Menus}
 * run against the live condition registry, so what the operator writes in {@code view} decides what the viewer sees.
 */
class ViewRequirementBlockGoldenTest {

    private static final int STORAGE_SLOTS = 36;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private MenuBindings bindings;
    private Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");

        Logger log = new NoopLogger();
        bindings = new MenuBindings();
        Currencies currencies = new Currencies(Hooks.resolve(server, log, List.of()), server, log, "vault");
        RequirementConditions.register(bindings, currencies, new PlayerMeta(plugin), log);
        // Two deterministic conditions so the flat-AND backward-compat case does not depend on inventory state.
        bindings.condition("cond-true", (ctx, args) -> true);
        bindings.condition("cond-false", (ctx, args) -> false);

        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMinimumBlockRendersWhenEitherConditionPasses() {
        // Three empty slots: has-empty-slots:1 passes but has-empty-slots:9 fails, so an OR block still shows the item.
        leaveEmptySlots(3);
        assertThat(render("view = { requirements = [\"has-empty-slots:1\", \"has-empty-slots:9\"], minimum = 1 }"))
                .as("minimum 1 (OR) renders when the first condition passes")
                .isNotNull()
                .extracting(ItemStack::getType)
                .isEqualTo(Material.DIAMOND);
    }

    @Test
    void aMinimumBlockIsHiddenWhenEveryConditionFails() {
        // A full inventory fails both empty-slot checks, so even an OR block hides the item.
        leaveEmptySlots(0);
        assertThat(render("view = { requirements = [\"has-empty-slots:1\", \"has-empty-slots:9\"], minimum = 1 }"))
                .as("an OR block hides the item only when every condition fails")
                .isNull();
    }

    @Test
    void aFlatListWithoutAMinimumIsStillAnAndAndHidesWhenOneConditionFails() {
        // The same two conditions with no minimum default to AND, so the failing has-empty-slots:9 hides the item.
        leaveEmptySlots(3);
        assertThat(render("view = [\"has-empty-slots:1\", \"has-empty-slots:9\"]"))
                .as("a flat view list is an AND, so one failing condition hides the item")
                .isNull();
    }

    @Test
    void anInvertedFlatViewHidesTheItemWhenTheConditionHolds() {
        // has-empty-slots:1 passes on a fresh inventory, so !has-empty-slots:1 is false and the item is hidden.
        leaveEmptySlots(STORAGE_SLOTS);
        assertThat(render("view = [\"!has-empty-slots:1\"]"))
                .as("an inverted condition hides the item while the underlying condition passes")
                .isNull();
    }

    @Test
    void anInvertedFlatViewShowsTheItemWhenTheConditionFails() {
        // With a full inventory has-empty-slots:1 fails, so !has-empty-slots:1 passes and the item shows.
        leaveEmptySlots(0);
        assertThat(render("view = [\"!has-empty-slots:1\"]"))
                .as("an inverted condition shows the item exactly when the underlying condition fails")
                .isNotNull()
                .extracting(ItemStack::getType)
                .isEqualTo(Material.DIAMOND);
    }

    @Test
    void aPlainFlatAndListBehavesExactlyAsBefore() {
        assertThat(render("view = [\"cond-true\"]"))
                .as("a single-condition flat view shows when it passes, as it always did")
                .isNotNull();
        assertThat(render("view = [\"cond-true\", \"cond-false\"]"))
                .as("a flat AND list hides the item when any one condition fails")
                .isNull();
    }

    @Test
    void startupValidationCatchesAnUnknownConditionInAViewBlock() {
        MenuSpec spec = parse("view = { requirements = [\"nope-unknown:1\"], minimum = 1 }");
        assertThat(bindings.validate(List.of(spec)))
                .as("a view block naming an unregistered condition is a loud startup failure")
                .contains("nope-unknown:1");
    }

    @Test
    void startupValidationPassesForAKnownConditionInAViewBlock() {
        MenuSpec spec = parse("view = { requirements = [\"has-money:100\"], minimum = 1 }");
        assertThat(bindings.validate(List.of(spec)))
                .as("has-money:100 counts as known because its head has-money is registered")
                .isEmpty();
    }

    /** Fill the first {@code STORAGE_SLOTS - empty} storage slots with stone so exactly {@code empty} stay clear. */
    private void leaveEmptySlots(int empty) {
        for (int slot = 0; slot < STORAGE_SLOTS - empty; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE));
        }
    }

    private ItemStack render(String viewClause) {
        menus.registerSpec("gated", parse(viewClause));
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "gated", null);
        return player.getOpenInventory().getTopInventory().getItem(0);
    }

    private static MenuSpec parse(String viewClause) {
        return new MenuSpecLoader()
                .parse("rows = 1\nitems {\n  gated { slot = 0, material = DIAMOND, name = \"x\", " + viewClause
                        + " }\n}\n");
    }

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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
