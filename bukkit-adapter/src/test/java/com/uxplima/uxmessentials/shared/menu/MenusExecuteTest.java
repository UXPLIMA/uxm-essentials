package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.outbound.EngineScheduler;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.ThemeFile;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.spec.Ref;
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
                .isThrownBy(() -> menus.execute(TestViewer.of(server.addPlayer().getUniqueId(), "x"), null));
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

        menus.execute(steve, Ref.parse("record:hi"));

        assertThat(ranFor.get()).isEqualTo("Steve");
        assertThat(ranArg.get()).isEqualTo("hi");
    }

    @Test
    void executeOnAnEngineWithoutAnActionRegistryIsANoOp() {
        // The list/spec-only engine carries no action registry, so execute runs nothing rather than failing.
        Menus menus = new Menus(renderer(), EngineScheduler.of(new SyncScheduler()), new ListSourceRegistry());
        PlayerMock steve = server.addPlayer("Steve");

        menus.execute(steve, Ref.parse("record:hi"));
        // No throw is the assertion; there is no registry to dispatch through.
    }

    /** An engine wired with {@code bindings}' action/condition registries and a synchronous scheduler. */
    private Menus engine(MenuBindings bindings) {
        return new Menus(
                renderer(),
                EngineScheduler.of(new SyncScheduler()),
                new ListSourceRegistry(),
                null,
                bindings.actions(),
                bindings.conditions(),
                null);
    }

    private static MenuRenderer renderer() {
        ItemRenderer itemRenderer =
                new ItemRenderer(new GuiText(new KeyMessages()), ThemeFile::shippedTheme, new PlaceholderRegistry());
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
