package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadCloseListener;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadView;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.VerificationFreezeListener;
import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.VerifyTwoFactor;
import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import com.uxplima.uxmessentials.security.domain.TotpCode;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.event.MenuOpenEvent;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the join-verification freeze end to end: an enrolled player on an untrusted device is frozen
 * on join and cannot move until they verify; the correct PIN (typed on the keypad) and a correct TOTP code both
 * unfreeze; a wrong code counts a failure and re-prompts; the configured number of failures locks the player out; a
 * trusted device skips the prompt entirely; and a non-enrolled player is never frozen.
 */
class JoinVerificationTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final String PIN = "1234";
    private static final int MAX_ATTEMPTS = 3;

    // The stable keypad slots for the digits used in the click-through test, and the submit button.
    private static final int SLOT_1 = 11;
    private static final int SLOT_2 = 13;
    private static final int SLOT_3 = 15;
    private static final int SLOT_4 = 20;
    private static final int SLOT_SUBMIT = 42;

    private ServerMock server;
    private Plugin plugin;
    private FakeRepository repository;
    private FakeTrustStore trustStore;
    private VerificationSessions sessions;
    private AttemptLimiter limiter;
    private ReauthState reauthState;
    private RecordingSink sink;
    private PinKeypadView keypad;
    private VerificationController controller;
    private VerificationFreezeListener freezeListener;
    private MenuListener menuListener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        repository = new FakeRepository();
        trustStore = new FakeTrustStore();
        sessions = new VerificationSessions();
        limiter = new AttemptLimiter(new LockoutPolicy(MAX_ATTEMPTS), Duration.ofMinutes(5));
        reauthState = new ReauthState();
        sink = new RecordingSink();
        Scheduler scheduler = new InlineScheduler();
        Messages messages = new KeyMessages();
        // The keypad renders through the real menu engine here, so a keypad click routes through the engine to the
        // registered security:pin-* actions exactly as it does in production: the click/drag cancel that locks the
        // window is the engine's, and the digit/submit buttons are its actions.
        GuiText guiText = new GuiText(messages);
        MenuBindings bindings = new MenuBindings();
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        menuListener = new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(menuListener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        keypad = new PinKeypadView(menus, messages, scheduler);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        controller = new VerificationController(
                repository,
                new VerifyTwoFactor(repository, 1),
                trustStore,
                sessions,
                limiter,
                reauthState,
                config(),
                keypad,
                new AutoSubmitTotpPrompt(),
                scheduler,
                messages,
                sink,
                clock);
        keypad.register(bindings, controller, specDir(), new NoopLogger());
        freezeListener = new VerificationFreezeListener(sessions, messages, sink);
        server.getPluginManager().registerEvents(freezeListener, plugin);
        server.getPluginManager().registerEvents(new PinKeypadCloseListener(menus, keypad, sessions), plugin);
    }

    /** The bundled spec directory under the source tree, so the test loads the shipped keypad spec from disk. */
    private static Path specDir() {
        Path repoRoot = Path.of("").toAbsolutePath();
        while (repoRoot != null && !Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
            repoRoot = repoRoot.getParent();
        }
        Objects.requireNonNull(repoRoot, "repo root");
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // MockBukkit's event-simulation helpers are marked for removal but remain the supported way to fire a move/click.
    @SuppressWarnings("removal")
    @Test
    void anEnrolledPlayerIsFrozenOnJoinAndCannotMove() {
        PlayerMock player = joinedPlayerWithPin();

        assertThat(sessions.isPending(player.getUniqueId())).isTrue();

        Location destination = player.getLocation().add(5, 0, 0);
        PlayerMoveEvent move = player.simulatePlayerMove(destination);
        assertThat(move.isCancelled()).isTrue();
    }

    @Test
    void aNonEnrolledPlayerIsNeverFrozen() {
        PlayerMock player = addPlayer();

        controller.onJoin(player);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
    }

    @Test
    void aTrustedDeviceSkipsThePrompt() {
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);
        trustStore.trust(player.getUniqueId(), IpHashing.hash("10.0.0.5"), NOW.plusSeconds(3600));

        controller.onJoin(player);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
    }

    // MockBukkit's event-simulation helpers are marked for removal but remain the supported way to fire a move/click.
    @SuppressWarnings("removal")
    @Test
    void theCorrectPinTypedOnTheKeypadUnfreezesThePlayer() {
        PlayerMock player = joinedPlayerWithPin();
        InventoryView view = player.getOpenInventory();

        player.simulateInventoryClick(view, SLOT_1);
        player.simulateInventoryClick(view, SLOT_2);
        player.simulateInventoryClick(view, SLOT_3);
        player.simulateInventoryClick(view, SLOT_4);
        player.simulateInventoryClick(view, SLOT_SUBMIT);

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(sink.delivered).contains("security.verify.success");
        // The verified device is remembered for the next join.
        assertThat(trustStore.isTrusted(player.getUniqueId(), IpHashing.hash("10.0.0.5"), NOW))
                .isTrue();
    }

    @Test
    void aDragAcrossTheKeypadIsCancelled() {
        PlayerMock player = joinedPlayerWithPin();
        InventoryView view = player.getOpenInventory();
        InventoryDragEvent drag = new InventoryDragEvent(
                view, null, new ItemStack(Material.STONE), false, Map.of(SLOT_1, new ItemStack(Material.STONE)));

        menuListener.onDrag(drag);

        assertThat(drag.isCancelled()).isTrue(); // the engine locks the frozen keypad: no item may be dragged in or out
    }

    // The frozen re-open invariant: a still-frozen player who escapes the keypad has it reopened, so verification can
    // never be slipped past by closing the window. The open is counted through the engine's MenuOpenEvent so the assert
    // does not hinge on MockBukkit's close-then-open inventory finalisation.
    @SuppressWarnings("removal")
    @Test
    void escapingTheKeypadWhileStillFrozenReopensIt() {
        OpenCounter opens = new OpenCounter();
        server.getPluginManager().registerEvents(opens, plugin);
        PlayerMock player = joinedPlayerWithPin();
        assertThat(opens.count).isEqualTo(1); // the first keypad open

        player.closeInventory(); // the frozen player tries to escape the keypad

        assertThat(sessions.isPending(player.getUniqueId())).isTrue(); // still frozen
        assertThat(opens.count).isEqualTo(2); // and the keypad was reopened
    }

    // The counterpart: once a deliberate close is flagged (the TOTP handoff, a verify success, a lockout, a stop), the
    // escaped-window reopen must not fight it, so a flagged close does not reopen.
    @SuppressWarnings("removal")
    @Test
    void aDeliberatelyFlaggedCloseIsNotReopened() {
        OpenCounter opens = new OpenCounter();
        server.getPluginManager().registerEvents(opens, plugin);
        PlayerMock player = joinedPlayerWithPin();
        assertThat(opens.count).isEqualTo(1);

        keypad.suppressNextClose(ref(player)); // e.g. the handoff to the TOTP prompt
        player.closeInventory();

        assertThat(opens.count).isEqualTo(1); // no reopen: the flagged close was left alone
    }

    @Test
    void aCorrectTotpCodeUnfreezesThePlayer() {
        PlayerMock player = addPlayer();
        TwoFactorSecret secret = new TwoFactorSecret("JBSWY3DPEHPK3PXP");
        repository.enableTotp(player.getUniqueId(), secret);
        controller.onJoin(player);
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();

        controller.submit(player, ref(player), TotpCode.generate(secret, NOW));

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(sink.delivered).contains("security.verify.success");
    }

    @Test
    void aWrongCodeCountsAFailureAndRePrompts() {
        PlayerMock player = joinedPlayerWithPin();

        controller.submit(player, ref(player), "0000");

        assertThat(sessions.isPending(player.getUniqueId())).isTrue(); // still frozen
        assertThat(sink.delivered).contains("security.verify.failed");
    }

    @Test
    void theConfiguredNumberOfFailuresLocksThePlayerOut() {
        PlayerMock player = joinedPlayerWithPin();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            controller.submit(player, ref(player), "0000");
        }

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(limiter.isLockedOut(player.getUniqueId(), NOW)).isTrue();
    }

    @Test
    void aLockedOutPlayerIsNotFrozenAgainButKeptOut() {
        PlayerMock player = joinedPlayerWithPin();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            controller.submit(player, ref(player), "0000");
        }

        controller.onJoin(player);

        // The rejoin is bounced by the lockout, not turned into a fresh freeze.
        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
        assertThat(limiter.isLockedOut(player.getUniqueId(), NOW)).isTrue();
    }

    // I-1: the failure budget must survive a disconnect/rejoin — guessing maxAttempts-1, relogging, then one more
    // guess must lock the account out, not hand the attacker a fresh set of attempts.
    @SuppressWarnings("removal")
    @Test
    void theLockoutSurvivesAReconnectAndCannotBeResetByRejoining() {
        PlayerMock player = joinedPlayerWithPin();

        // Two wrong guesses (maxAttempts - 1), then disconnect before the lockout-triggering attempt.
        for (int attempt = 0; attempt < MAX_ATTEMPTS - 1; attempt++) {
            controller.submit(player, ref(player), "0000");
        }
        assertThat(limiter.isLockedOut(player.getUniqueId(), NOW)).isFalse();
        controller.onQuit(ref(player));
        player.closeInventory(); // the disconnect drops the open keypad; the quit already cleared the freeze

        // Reconnect: a fresh join re-freezes the player but must NOT reset the accumulated failures.
        controller.onJoin(player);
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();

        controller.submit(player, ref(player), "0000"); // the maxAttempts-th failure across the two sessions

        assertThat(limiter.isLockedOut(player.getUniqueId(), NOW)).isTrue();
    }

    // I-3: the freeze is established synchronously on join. Before the async enrolment lookup resolves an enrolled
    // player is already pending (a command is cancelled); a non-enrolled player is cleared once the lookup runs.
    @SuppressWarnings("removal")
    @Test
    void anEnrolledPlayerIsFrozenSynchronouslyBeforeTheAsyncLookupResolves() {
        DeferringScheduler deferred = new DeferringScheduler();
        VerificationController optimistic = optimisticController(deferred);
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);

        optimistic.onJoin(player); // async decision is queued, not yet run

        // In the synchronous window the player is already frozen: a command they fire is cancelled.
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();
        PlayerCommandPreprocessEvent command = new PlayerCommandPreprocessEvent(player, "/spawn");
        server.getPluginManager().callEvent(command);
        assertThat(command.isCancelled()).isTrue();

        // Once the async lookup resolves, the enrolled player stays frozen.
        deferred.runQueued();
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();
    }

    @Test
    void aNonEnrolledPlayerIsFrozenOptimisticallyThenClearedWhenTheLookupResolves() {
        DeferringScheduler deferred = new DeferringScheduler();
        VerificationController optimistic = optimisticController(deferred);
        PlayerMock player = addPlayer(); // no factor enrolled

        optimistic.onJoin(player);

        // The optimistic freeze applies to everyone in the synchronous window — "frozen until proven safe".
        assertThat(sessions.isPending(player.getUniqueId())).isTrue();

        deferred.runQueued(); // the lookup finds no factor and lifts the freeze

        assertThat(sessions.isPending(player.getUniqueId())).isFalse();
    }

    private VerificationController optimisticController(Scheduler scheduler) {
        return new VerificationController(
                repository,
                new VerifyTwoFactor(repository, 1),
                trustStore,
                sessions,
                limiter,
                reauthState,
                config(),
                keypad,
                new AutoSubmitTotpPrompt(),
                scheduler,
                new KeyMessages(),
                sink,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PlayerMock joinedPlayerWithPin() {
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);
        controller.onJoin(player);
        return player;
    }

    private PlayerMock addPlayer() {
        PlayerMock player = server.addPlayer();
        player.setAddress(new InetSocketAddress("10.0.0.5", 30_000));
        return player;
    }

    private static PlayerRef ref(PlayerMock player) {
        return BukkitRefs.toRef(player);
    }

    private static SecurityConfig.JoinVerification config() {
        return new SecurityConfig.JoinVerification(
                true, true, Duration.ofHours(24), MAX_ATTEMPTS, Duration.ofMinutes(5));
    }

    /** A no-op TOTP prompt for the tests that do not exercise the anvil/chat handoff. */
    private static final class AutoSubmitTotpPrompt implements TotpPrompt {
        @Override
        public void prompt(
                org.bukkit.entity.Player player, PlayerRef viewer, Consumer<String> onSubmit, Runnable onCancel) {
            // The keypad digit path covers verification; this seam is left inert here.
        }
    }

    /** Counts every keypad open the engine fires, so the reopen invariant is asserted through the engine's own event. */
    private static final class OpenCounter implements Listener {
        private int count;

        // Invoked reflectively by Bukkit's event bus, so it reads as unused to static analysis.
        @SuppressWarnings("UnusedMethod")
        @EventHandler
        public void onOpen(MenuOpenEvent event) {
            if (event.getMenuId().equals(PinKeypadView.SPEC_ID)) {
                count++;
            }
        }
    }

    /** Swallows the menu-spec loader's diagnostics; the shipped keypad spec loads cleanly from the source tree. */
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

    /** An in-memory two-factor store mirroring the jOOQ store's contract, keeping the PIN as plaintext for the test. */
    private static final class FakeRepository implements TwoFactorRepository {
        private record Row(
                @Nullable TwoFactorSecret secret, @Nullable String pin) {}

        private final Map<UUID, Row> rows = new HashMap<>();

        @Override
        public Optional<TwoFactorRegistration> find(UUID playerId) {
            Row row = rows.get(playerId);
            return row == null
                    ? Optional.empty()
                    : Optional.of(new TwoFactorRegistration(playerId, row.secret(), row.pin() != null, NOW));
        }

        @Override
        public void enableTotp(UUID playerId, TwoFactorSecret secret) {
            Row existing = rows.get(playerId);
            rows.put(playerId, new Row(secret, existing == null ? null : existing.pin()));
        }

        @Override
        public void setPin(UUID playerId, String plaintextPin) {
            Row existing = rows.get(playerId);
            rows.put(playerId, new Row(existing == null ? null : existing.secret(), plaintextPin));
        }

        @Override
        public boolean verifyPin(UUID playerId, String candidate) {
            Row row = rows.get(playerId);
            return row != null && row.pin() != null && row.pin().equals(candidate);
        }

        @Override
        public void delete(UUID playerId) {
            rows.remove(playerId);
        }
    }

    /** An in-memory device-trust store keyed by player-and-hash, with an expiry. */
    private static final class FakeTrustStore implements TrustStore {
        private final Map<String, Instant> trusts = new HashMap<>();

        @Override
        public boolean isTrusted(UUID playerId, String ipHash, Instant now) {
            Instant until = trusts.get(playerId + "|" + ipHash);
            return until != null && now.isBefore(until);
        }

        @Override
        public void trust(UUID playerId, String ipHash, Instant until) {
            trusts.put(playerId + "|" + ipHash, until);
        }

        @Override
        public void revoke(UUID playerId) {
            trusts.keySet().removeIf(key -> key.startsWith(playerId + "|"));
        }
    }

    /** Resolves every key to its dotted catalog id so the tests can assert which message was delivered. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Records every delivered message so a test can assert the player saw the expected line. */
    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    /** Runs every scheduler hop inline so the async verify/DB work resolves synchronously in the test. */
    private static final class InlineScheduler implements Scheduler {
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

    /** Defers {@code async} work to a manual drain so the synchronous join window can be inspected before it runs. */
    private static final class DeferringScheduler implements Scheduler {
        private final List<Runnable> queued = new ArrayList<>();

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
            queued.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            queued.add(task);
        }

        void runQueued() {
            List<Runnable> snapshot = new ArrayList<>(queued);
            queued.clear();
            snapshot.forEach(Runnable::run);
        }
    }
}
