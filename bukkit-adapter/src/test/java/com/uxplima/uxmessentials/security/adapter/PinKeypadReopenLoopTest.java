package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;

import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadView;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadWindowListener;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
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
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The keypad has to survive being reopened on top of itself, because that is what the enrolment flow does between
 * the two entries and what the close listener does when a frozen player escapes the window. Opening an inventory
 * over an open one makes the server close the old one first, so a reopen that is not recognised as our own feeds
 * straight back into the listener that triggered it.
 *
 * <p>The scheduler here defers, which is what the real one does: the open the view asks for lands on a later tick,
 * not inside the call. A test whose scheduler runs inline cannot see this at all.
 */
class PinKeypadReopenLoopTest {

    @TempDir
    Path dataFolder;

    private ServerMock server;
    private PlayerMock player;
    private PlayerRef viewer;
    private DeferringScheduler scheduler;
    private Menus menus;
    private PinKeypadView view;
    private OpenCounter opens;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Frozen");
        viewer = BukkitRefs.toRef(player);
        scheduler = new DeferringScheduler();

        GuiText guiText = new GuiText(new KeyMessages());
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, new PlaceholderRegistry()), new ConditionRegistry());
        menus = new Menus(renderer, scheduler, new ListSourceRegistry(), null, null, new ConditionRegistry());
        Logger log = new SilentLogger();
        menus.registerSpec(
                PinKeypadView.CREATE_SPEC_ID,
                MenuSpecs.loadOrBundled("modules/security/gui/pin-create.conf", dataFolder, 4, log));

        view = new PinKeypadView(
                menus,
                new KeyMessages(),
                new VerificationFeedback(
                        new SecurityConfig.Feedback(false, "x", "x", "x", "x"), scheduler, new KeyMessages(), log),
                scheduler);

        opens = new OpenCounter();
        server.getPluginManager().registerEvents(opens, MockBukkit.createMockPlugin());
        server.getPluginManager()
                .registerEvents(
                        new PinKeypadWindowListener(menus, view, alwaysPending()), MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openingTheCreatePadOverItselfSettlesInsteadOfLooping() {
        view.openCreate(player, viewer);
        scheduler.drain();
        assertThat(opens.count()).isEqualTo(1);

        // What the enrolment flow used to do between the first and the second entry: a fresh pad while one is open.
        // The server closes the first to make room, and that close must not read as the player escaping.
        view.openCreate(player, viewer);
        scheduler.drain();

        assertThat(opens.count()).isEqualTo(2);
    }

    @Test
    void aStillFrozenPlayerWhoEscapesTheWindowGetsItBackExactlyOnce() {
        view.openCreate(player, viewer);
        scheduler.drain();

        player.closeInventory();
        scheduler.drain();

        assertThat(opens.count()).isEqualTo(2);
        assertThat(menus.currentMenu(viewer.uuid()).map(info -> info.specId())).contains(PinKeypadView.CREATE_SPEC_ID);
    }

    @Test
    void theSecondStepOfEnrolmentDoesNotPutUpASecondWindow() {
        view.openCreate(player, viewer);
        scheduler.drain();

        // takeFirst/restart route through this: the pad is already on screen, so nothing is reopened.
        view.ensureCreateOpen(player, viewer);
        scheduler.drain();

        assertThat(opens.count()).isEqualTo(1);
    }

    /** A still-frozen player, which is what an enrolling player is: the freeze is only lifted once a PIN exists. */
    private VerificationSessions alwaysPending() {
        VerificationSessions sessions = new VerificationSessions();
        sessions.begin(viewer.uuid());
        return sessions;
    }

    static final class OpenCounter implements Listener {

        private int count;

        @EventHandler
        public void onOpen(InventoryOpenEvent event) {
            count++;
        }

        int count() {
            return count;
        }
    }

    /**
     * Defers every hop, then runs what is due; a task queued while draining lands in the next drain. Bounded so a
     * genuine loop fails the test instead of hanging the build.
     */
    private static final class DeferringScheduler implements Scheduler {

        private static final int MAX_ROUNDS = 8;

        private final List<Runnable> pending = new ArrayList<>();

        void drain() {
            for (int round = 0; round < MAX_ROUNDS && !pending.isEmpty(); round++) {
                List<Runnable> due = List.copyOf(pending);
                pending.clear();
                due.forEach(Runnable::run);
            }
        }

        @Override
        public void onGlobal(Runnable task) {
            pending.add(task);
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            pending.add(task);
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            pending.add(task);
        }

        @Override
        public void async(Runnable task) {
            pending.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            pending.add(task);
        }
    }

    private static final class KeyMessages implements Messages {

        @Override
        public String resolve(PlayerRef viewer, MessageKey lookup, Map<String, String> placeholders) {
            return lookup.key();
        }
    }

    private static final class SilentLogger implements Logger {

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
