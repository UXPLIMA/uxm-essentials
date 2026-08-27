package com.uxplima.uxmessentials.customcommands;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.customcommands.adapter.outbound.MenuActionRunner;
import com.uxplima.uxmessentials.customcommands.adapter.outbound.MenuActionRunner.PrivilegedActions;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
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
 * What a custom command's chain does on its way to the menu engine: argument tokens are expanded before the action
 * is dispatched, a delayed step is scheduled rather than run inline, and the two privileged heads obey the module's
 * policy instead of the file that names them.
 */
class MenuActionRunnerTest {

    private static final ActionChain.ChainLimits LIMITS = ActionChain.ChainLimits.defaults();

    private ServerMock server;
    private PlayerRef steve;
    private RecordingActions registry;
    private DeferringScheduler scheduler;
    private RecordingLogger log;
    private AtomicReference<PrivilegedActions> policy;
    private Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PlayerMock player = server.addPlayer("Steve");
        steve = BukkitRefs.toRef(player);
        registry = new RecordingActions();
        scheduler = new DeferringScheduler();
        log = new RecordingLogger();
        policy = new AtomicReference<>(PrivilegedActions.defaults());
        GuiText guiText = new GuiText(new KeyMessages());
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, new PlaceholderRegistry()), new ConditionRegistry());
        menus = new Menus(
                renderer, scheduler, new ListSourceRegistry(), null, registry.registry(), new ConditionRegistry());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void expandsArgumentTokensInsideTheActionItselfBeforeDispatch() {
        runner().run(
                        steve,
                        chain("console:give %arg_target% diamond %arg_amount%"),
                        Map.of("target", "Alex", "amount", "3"));

        assertThat(registry.dispatched()).containsExactly("console:give Alex diamond 3");
    }

    @Test
    void expandsTheRawRemainderToken() {
        runner().run(steve, chain("command:gamemode creative %args%"), Map.of("args", "Alex"));

        assertThat(registry.dispatched()).containsExactly("command:gamemode creative Alex");
    }

    @Test
    void aStepWithAnOffsetIsScheduledRatherThanRunNow() {
        runner().run(steve, ActionChain.of(List.of("message:one", "delay:2s", "message:two"), LIMITS), Map.of());

        assertThat(registry.dispatched()).containsExactly("message:one");
        assertThat(scheduler.pending()).hasSize(1);

        scheduler.runPending();

        assertThat(registry.dispatched()).containsExactly("message:one", "message:two");
    }

    @Test
    void aConsoleActionIsSkippedAndLoggedWhenTheModuleForbidsIt() {
        policy.set(new PrivilegedActions(false, false, true));

        runner().run(steve, chain("console:say hello"), Map.of());

        assertThat(registry.dispatched()).isEmpty();
        assertThat(log.warnings()).anyMatch(line -> line.contains("console"));
    }

    @Test
    void anOpActionIsSkippedByDefaultBecauseItShipsOff() {
        runner().run(steve, chain("command-as-op:gamemode creative"), Map.of());

        assertThat(registry.dispatched()).isEmpty();
    }

    @Test
    void anAuditedPrivilegedActionRecordsWhoTriggeredIt() {
        policy.set(new PrivilegedActions(true, false, true));

        runner().run(steve, chain("console:say hello"), Map.of());

        assertThat(log.infos()).anyMatch(line -> line.contains("Steve") && line.contains("console"));
    }

    private MenuActionRunner runner() {
        return new MenuActionRunner(menus, scheduler, log, policy::get);
    }

    private static ActionChain chain(String token) {
        return ActionChain.of(List.of(token), LIMITS);
    }

    /** An action registry whose handlers record the token they were dispatched with, head and value alike. */
    private static final class RecordingActions {

        private final ActionRegistry registry = new ActionRegistry();
        private final List<String> dispatched = new ArrayList<>();

        RecordingActions() {
            for (String id : List.of("console", "command", "message", "command-as-op")) {
                registry.register(
                        id, ctx -> dispatched.add(id + ":" + ctx.args().getOrDefault("value", "")));
            }
        }

        ActionRegistry registry() {
            return registry;
        }

        List<String> dispatched() {
            return dispatched;
        }
    }

    /** A scheduler that runs immediate work inline and holds delayed work until a test releases it. */
    private static final class DeferringScheduler implements Scheduler {

        private final List<Runnable> pending = new ArrayList<>();

        List<Runnable> pending() {
            return pending;
        }

        void runPending() {
            List<Runnable> due = List.copyOf(pending);
            pending.clear();
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
            pending.add(task);
        }
    }

    /** A logger double keeping both levels, since one test asserts on the audit line and another on the refusal. */
    private static final class RecordingLogger implements Logger {

        private final List<String> infos = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        List<String> infos() {
            return infos;
        }

        List<String> warnings() {
            return warnings;
        }

        @Override
        public void info(String message, Object... args) {
            infos.add(render(message, args));
        }

        @Override
        public void warn(String message, Object... args) {
            warnings.add(render(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}

        private static String render(String message, Object... args) {
            String out = message;
            for (Object arg : args) {
                out = out.replaceFirst("\\{}", String.valueOf(arg));
            }
            return out;
        }
    }

    /** A pass-through messages double: returns the key verbatim so no catalog is needed. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
