package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadView;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.ReauthCommandListener;
import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.VerifyTwoFactor;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import com.uxplima.uxmessentials.security.domain.ReauthPolicy;
import com.uxplima.uxmessentials.security.domain.TotpCode;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
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
 * MockBukkit coverage of op-command protection: an enrolled player running a protected command they have not verified
 * for is blocked and prompted to re-authenticate; a correct proof stamps the window, clears the prompt and retries the
 * command; a command inside the window, a non-protected command, and a holder of the bypass node all pass; the match
 * ignores a leading slash, arguments and a namespace prefix; and the disabled gate never cancels.
 */
class OpCommandProtectionTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final String PIN = "1234";
    private static final Duration WINDOW = Duration.ofSeconds(60);
    private static final String BYPASS = "uxmessentials.security.bypass";
    private static final int MAX_ATTEMPTS = 3;

    private ServerMock server;
    private Plugin plugin;
    private FakeRepository repository;
    private AttemptLimiter limiter;
    private ReauthState reauthState;
    private ReauthSessions reauthSessions;
    private RecordingSink sink;
    private ReauthController reauthController;
    private ReauthPolicy policy;
    private ReauthCommandListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        repository = new FakeRepository();
        limiter = new AttemptLimiter(new LockoutPolicy(MAX_ATTEMPTS), Duration.ofMinutes(5));
        reauthState = new ReauthState();
        reauthSessions = new ReauthSessions();
        sink = new RecordingSink();
        Scheduler scheduler = new InlineScheduler();
        Messages messages = new KeyMessages();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        // The keypad renders through the real menu engine; the re-auth flow opens it as a side effect (the tests drive
        // the proof through the controller directly), so the engine is wired and the shipped spec registered.
        MenuBindings bindings = new MenuBindings();
        MenuRenderer renderer = new MenuRenderer(
                new ItemRenderer(new GuiText(messages), bindings.placeholders()), bindings.conditions());
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        PinKeypadView keypad = new PinKeypadView(menus, messages, scheduler);
        reauthController = new ReauthController(
                repository,
                new VerifyTwoFactor(repository, 1),
                limiter,
                reauthState,
                reauthSessions,
                keypad,
                new AutoSubmitTotpPrompt(),
                scheduler,
                messages,
                sink,
                clock);
        keypad.register(bindings, reauthController, specDir(), new NoopLogger());
        policy = new ReauthPolicy(Set.of("op", "gamemode", "stop"), WINDOW);
        listener = new ReauthCommandListener(true, policy, reauthState, reauthController, clock);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aProtectedCommandIsBlockedWhenNotRecentlyVerified() {
        PlayerMock player = enrolledWithPin();

        PlayerCommandPreprocessEvent event = fire(player, "/op Steve");

        assertThat(event.isCancelled()).isTrue();
        assertThat(reauthSessions.isPending(player.getUniqueId())).isTrue();
        assertThat(sink.delivered).contains("security.reauth.required");
    }

    @Test
    void aProtectedCommandIsAllowedWithinTheReauthWindow() {
        PlayerMock player = enrolledWithPin();
        reauthState.stamp(player.getUniqueId(), NOW);

        PlayerCommandPreprocessEvent event = fire(player, "/op Steve");

        assertThat(event.isCancelled()).isFalse();
        assertThat(reauthSessions.isPending(player.getUniqueId())).isFalse();
    }

    @Test
    void aSuccessfulReauthStampsTheWindowClearsTheSessionAndTellsThePlayer() {
        PlayerMock player = enrolledWithPin();
        fire(player, "/op Steve");
        assertThat(reauthSessions.isPending(player.getUniqueId())).isTrue();

        reauthController.submit(player, ref(player), PIN);

        assertThat(reauthSessions.isPending(player.getUniqueId())).isFalse();
        assertThat(reauthState.lastVerified(player.getUniqueId())).isEqualTo(NOW);
        assertThat(sink.delivered).contains("security.reauth.success");
    }

    @Test
    void aWrongProofKeepsTheReauthPending() {
        PlayerMock player = enrolledWithPin();
        fire(player, "/op Steve");

        reauthController.submit(player, ref(player), "0000");

        assertThat(reauthSessions.isPending(player.getUniqueId())).isTrue();
        assertThat(reauthState.lastVerified(player.getUniqueId())).isNull();
        assertThat(sink.delivered).contains("security.reauth.failed");
    }

    // I-2: the re-auth keypad shares the durable lockout. Enough wrong proofs lock the account, dropping the prompt and
    // refusing further attempts on the same shared budget.
    @Test
    void repeatedWrongProofsLockOutTheReauthPath() {
        PlayerMock player = enrolledWithPin();
        fire(player, "/op Steve");

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            reauthController.submit(player, ref(player), "0000");
        }

        assertThat(limiter.isLockedOut(player.getUniqueId(), NOW)).isTrue();
        assertThat(reauthSessions.isPending(player.getUniqueId())).isFalse();
        assertThat(sink.delivered).contains("security.reauth.locked-out");
    }

    @Test
    void aLockedOutAccountIsRefusedTheProtectedCommandWithoutAPrompt() {
        PlayerMock player = enrolledWithPin();
        // Exhaust the shared budget directly, as failures on any surface would.
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            limiter.recordFailure(player.getUniqueId(), NOW);
        }

        PlayerCommandPreprocessEvent event = fire(player, "/op Steve");

        assertThat(event.isCancelled()).isTrue(); // the synchronous guard still blocks the command
        assertThat(reauthSessions.isPending(player.getUniqueId())).isFalse(); // but no re-auth prompt is opened
        assertThat(sink.delivered).contains("security.reauth.locked-out");
    }

    @Test
    void aCorrectTotpProofAlsoReauthenticates() {
        PlayerMock player = addPlayer();
        TwoFactorSecret secret = new TwoFactorSecret("JBSWY3DPEHPK3PXP");
        repository.enableTotp(player.getUniqueId(), secret);
        fire(player, "/gamemode creative");

        reauthController.submit(player, ref(player), TotpCode.generate(secret, NOW));

        assertThat(reauthSessions.isPending(player.getUniqueId())).isFalse();
        assertThat(reauthState.lastVerified(player.getUniqueId())).isEqualTo(NOW);
    }

    @Test
    void theBypassPermissionExemptsAProtectedCommand() {
        PlayerMock player = enrolledWithPin();
        player.addAttachment(plugin, BYPASS, true);

        PlayerCommandPreprocessEvent event = fire(player, "/op Steve");

        assertThat(event.isCancelled()).isFalse();
        assertThat(reauthSessions.isPending(player.getUniqueId())).isFalse();
    }

    @Test
    void aNonProtectedCommandPasses() {
        PlayerMock player = enrolledWithPin();

        PlayerCommandPreprocessEvent event = fire(player, "/spawn");

        assertThat(event.isCancelled()).isFalse();
        assertThat(reauthSessions.isPending(player.getUniqueId())).isFalse();
    }

    @Test
    void matchingIgnoresArgumentsLeadingSlashAndNamespace() {
        PlayerMock player = enrolledWithPin();

        assertThat(fire(player, "/gamemode creative Steve").isCancelled()).isTrue();
        assertThat(fire(player, "/minecraft:stop").isCancelled()).isTrue();
    }

    @Test
    void theDisabledGateIsANoOp() {
        PlayerMock player = enrolledWithPin();
        ReauthCommandListener disabled = new ReauthCommandListener(
                false, policy, reauthState, reauthController, Clock.fixed(NOW, ZoneOffset.UTC));

        PlayerCommandPreprocessEvent event = fireVia(disabled, player, "/op Steve");

        assertThat(event.isCancelled()).isFalse();
        assertThat(reauthSessions.isPending(player.getUniqueId())).isFalse();
    }

    @Test
    void anUnenrolledPlayerIsAllowedThroughAndTheWindowIsStamped() {
        PlayerMock player = addPlayer(); // no factor enrolled

        fire(player, "/op Steve");

        // The synchronous cancel is undone by the async retry: with nothing to prove against, the command is allowed
        // and the window stamped so the retry (and any follow-up) runs.
        assertThat(reauthSessions.isPending(player.getUniqueId())).isFalse();
        assertThat(reauthState.lastVerified(player.getUniqueId())).isEqualTo(NOW);
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

    private PlayerMock enrolledWithPin() {
        PlayerMock player = addPlayer();
        repository.setPin(player.getUniqueId(), PIN);
        return player;
    }

    private PlayerMock addPlayer() {
        return server.addPlayer();
    }

    private PlayerCommandPreprocessEvent fire(Player player, String message) {
        return fireVia(listener, player, message);
    }

    private static PlayerCommandPreprocessEvent fireVia(ReauthCommandListener target, Player player, String message) {
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, message, null);
        target.onCommand(event);
        return event;
    }

    private static PlayerRef ref(PlayerMock player) {
        return BukkitRefs.toRef(player);
    }

    /** A no-op TOTP prompt for the tests that do not exercise the anvil/chat handoff. */
    private static final class AutoSubmitTotpPrompt implements TotpPrompt {
        @Override
        public void prompt(Player player, PlayerRef viewer, Consumer<String> onSubmit, Runnable onCancel) {
            // The keypad/direct-submit path covers verification; this seam is left inert here.
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
}
