package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.InventoryView;

import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadListener;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadView;
import com.uxplima.uxmessentials.security.adapter.inbound.listener.VerificationFreezeListener;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.VerifyTwoFactor;
import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.TotpCode;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
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
    private FakeRepository repository;
    private FakeTrustStore trustStore;
    private VerificationSessions sessions;
    private RecordingSink sink;
    private PinKeypadView keypad;
    private VerificationController controller;
    private VerificationFreezeListener freezeListener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        repository = new FakeRepository();
        trustStore = new FakeTrustStore();
        sessions = new VerificationSessions();
        sink = new RecordingSink();
        Scheduler scheduler = new InlineScheduler();
        Messages messages = new KeyMessages();
        keypad = new PinKeypadView(messages, scheduler);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        controller = new VerificationController(
                repository,
                new VerifyTwoFactor(repository, 1),
                trustStore,
                sessions,
                config(),
                keypad,
                new AutoSubmitTotpPrompt(),
                scheduler,
                messages,
                sink,
                clock);
        freezeListener = new VerificationFreezeListener(sessions, messages, sink);
        server.getPluginManager().registerEvents(freezeListener, MockBukkit.createMockPlugin("Freeze"));
        server.getPluginManager()
                .registerEvents(
                        new PinKeypadListener(keypad, controller, sessions), MockBukkit.createMockPlugin("Keypad"));
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
        assertThat(sessions.isLockedOut(player.getUniqueId(), NOW)).isTrue();
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
        assertThat(sessions.isLockedOut(player.getUniqueId(), NOW)).isTrue();
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
