package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
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
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Coverage of the two admin-tooling seams added to {@link Menus}: {@link Menus#registeredSpec(String)}, the
 * read-only lookup {@code /menu dump} and {@code /menu meta} use, and {@link Menus#execute(PlayerRef, Ref)}, the
 * standalone action runner behind {@code /menu execute}. {@code execute} runs one action on the target's own entity
 * thread through the shared action runner, so with a wired action registry and a synchronous scheduler the action
 * fires against the target's context; on an engine wired without an action registry it is a no-op.
 */
class MenusExecuteTest {

    private static final String SPEC_HOCON = """
            rows = 1
            items { one { slot = 0, material = STONE, name = "n" } }
            """;

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registeredSpecReturnsTheRegisteredSpecOrEmpty() {
        Menus menus = engine(new MenuBindings());
        MenuSpec spec = new MenuSpecLoader().parse(SPEC_HOCON);
        menus.registerSpec("shop", spec);

        assertThat(menus.registeredSpec("shop")).contains(spec);
        assertThat(menus.registeredSpec("ghost")).isEmpty();
    }

    @Test
    void executeRejectsNullInputs() {
        Menus menus = engine(new MenuBindings());

        assertThatNullPointerException().isThrownBy(() -> menus.execute(null, Ref.parse("record:hi")));
        assertThatNullPointerException()
                .isThrownBy(() -> menus.execute(new PlayerRef(server.addPlayer().getUniqueId(), "x"), null));
    }

    @Test
    void executeRunsTheActionForTheTargetContext() {
        AtomicReference<String> ranFor = new AtomicReference<>();
        AtomicReference<String> ranArg = new AtomicReference<>();
        MenuBindings bindings = new MenuBindings();
        bindings.action("record", c -> {
            ranFor.set(c.player().getName());
            ranArg.set(c.arg());
        });
        Menus menus = engine(bindings);
        PlayerMock steve = server.addPlayer("Steve");

        menus.execute(new PlayerRef(steve.getUniqueId(), steve.getName()), Ref.parse("record:hi"));

        assertThat(ranFor.get()).isEqualTo("Steve");
        assertThat(ranArg.get()).isEqualTo("hi");
    }

    @Test
    void executeOnAnEngineWithoutAnActionRegistryIsANoOp() {
        // The list/spec-only engine carries no action registry, so execute runs nothing rather than failing.
        Menus menus = new Menus(renderer(), new SyncScheduler(), new ListSourceRegistry());
        PlayerMock steve = server.addPlayer("Steve");

        menus.execute(new PlayerRef(steve.getUniqueId(), steve.getName()), Ref.parse("record:hi"));
        // No throw is the assertion; there is no registry to dispatch through.
    }

    /** An engine wired with {@code bindings}' action/condition registries and a synchronous scheduler. */
    private Menus engine(MenuBindings bindings) {
        return new Menus(
                renderer(),
                new SyncScheduler(),
                new ListSourceRegistry(),
                null,
                bindings.actions(),
                bindings.conditions(),
                null);
    }

    private static MenuRenderer renderer() {
        ItemRenderer itemRenderer = new ItemRenderer(new GuiText(new KeyMessages()), new PlaceholderRegistry());
        return new MenuRenderer(itemRenderer, new ConditionRegistry());
    }

    /** A pass-through messages double: every key resolves to its own key string. */
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
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
