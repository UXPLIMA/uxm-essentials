package com.uxplima.uxmessentials.security.adapter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.security.adapter.inbound.gui.KeypadActions;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadView;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.security.application.VerifyResult;
import com.uxplima.uxmessentials.security.application.VerifyTwoFactor;
import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The brain of the join-verification freeze: it decides on join whether a player must prove their second factor,
 * freezes them by marking a {@link VerificationSessions} entry, and drives the outcome of every submitted PIN or code
 * through to an unfreeze, a re-prompt, or a lockout kick. The keypad and the listeners are the hands — this class holds
 * the judgement the GUI has no business knowing.
 *
 * <p>Every DB read (the registration, the device-trust check) and write (recording a trust) runs off the tick thread
 * through the injected {@link Scheduler}, and every player touch (the prompt, the keypad, the kick) hops back onto the
 * player's region thread — so the flow is Folia-safe and never blocks a tick on I/O. A submitted PIN or code is held
 * only for the length of the verify and is never logged.
 */
@NullMarked
public final class VerificationController implements KeypadActions {

    private final TwoFactorRepository repository;
    private final VerifyTwoFactor verify;
    private final TrustStore trustStore;
    private final VerificationSessions sessions;
    private final ReauthState reauthState;
    private final SecurityConfig.JoinVerification config;
    private final PinKeypadView keypad;
    private final TotpPrompt totpPrompt;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink sink;
    private final Clock clock;

    public VerificationController(
            TwoFactorRepository repository,
            VerifyTwoFactor verify,
            TrustStore trustStore,
            VerificationSessions sessions,
            ReauthState reauthState,
            SecurityConfig.JoinVerification config,
            PinKeypadView keypad,
            TotpPrompt totpPrompt,
            Scheduler scheduler,
            Messages messages,
            MessageSink sink,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.verify = Objects.requireNonNull(verify, "verify");
        this.trustStore = Objects.requireNonNull(trustStore, "trustStore");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.reauthState = Objects.requireNonNull(reauthState, "reauthState");
        this.config = Objects.requireNonNull(config, "config");
        this.keypad = Objects.requireNonNull(keypad, "keypad");
        this.totpPrompt = Objects.requireNonNull(totpPrompt, "totpPrompt");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Decide, off the tick thread, whether {@code player} must verify on this join and freeze them if so. */
    public void onJoin(Player player) {
        Objects.requireNonNull(player, "player");
        if (!config.enabled()) {
            return;
        }
        PlayerRef ref = BukkitRefs.toRef(player);
        String ipHash = ipHash(player);
        scheduler.async(() -> decideJoin(ref, ipHash));
    }

    /** Drop a leaving player's freeze so a disconnect mid-verification leaves no lingering pending entry. */
    public void onQuit(PlayerRef viewer) {
        sessions.clear(Objects.requireNonNull(viewer, "viewer").uuid());
    }

    @Override
    public void submit(Player player, PlayerRef viewer, String candidate) {
        verifySubmission(player, viewer, candidate, false);
    }

    @Override
    public void requestTotp(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        if (!sessions.isPending(viewer.uuid())) {
            return;
        }
        // The prompt (anvil) replaces the keypad window; mark the resulting close as a handoff so it is not reopened.
        keypad.suppressNextClose(viewer);
        totpPrompt.prompt(
                player,
                viewer,
                code -> verifySubmission(player, viewer, code, true),
                () -> reopenKeypad(player, viewer));
    }

    private void decideJoin(PlayerRef ref, @Nullable String ipHash) {
        Instant now = clock.instant();
        if (sessions.isLockedOut(ref.uuid(), now)) {
            scheduler.onEntity(ref, () -> kick(ref, SecurityMessageKey.SECURITY_VERIFY_LOCKED_OUT));
            return;
        }
        TwoFactorRegistration registration = repository.find(ref.uuid()).orElse(null);
        if (registration == null || !registration.hasAnyFactor()) {
            return; // not enrolled — never frozen
        }
        if (config.trustDevices() && ipHash != null && trustStore.isTrusted(ref.uuid(), ipHash, now)) {
            return; // a trusted device skips the prompt
        }
        sessions.begin(ref.uuid());
        boolean totpEnabled = registration.totpEnabled();
        scheduler.onEntity(ref, () -> beginFreeze(ref, totpEnabled));
    }

    private void beginFreeze(PlayerRef ref, boolean totpEnabled) {
        Player live = Bukkit.getPlayer(ref.uuid());
        if (live == null || !live.isOnline()) {
            sessions.clear(ref.uuid());
            return;
        }
        notify(ref, SecurityMessageKey.SECURITY_VERIFY_PROMPT);
        keypad.open(live, ref, totpEnabled);
    }

    private void verifySubmission(Player player, PlayerRef viewer, String candidate, boolean reopenOnFailure) {
        if (!sessions.isPending(viewer.uuid())) {
            return;
        }
        scheduler.async(() -> {
            VerifyResult result = verify.verify(viewer.uuid(), candidate, clock.instant());
            scheduler.onEntity(viewer, () -> applyResult(player, viewer, result, reopenOnFailure));
        });
    }

    private void applyResult(Player player, PlayerRef viewer, VerifyResult result, boolean reopenOnFailure) {
        switch (result) {
            case SUCCESS, NOT_ENROLLED -> succeed(player, viewer);
            case INVALID -> fail(player, viewer, reopenOnFailure);
        }
    }

    private void succeed(Player player, PlayerRef viewer) {
        sessions.clear(viewer.uuid());
        // A fresh join proof also opens the op-command re-auth window, so the player is not re-asked to verify to run
        // a protected command they were just about to run.
        reauthState.stamp(viewer.uuid(), clock.instant());
        keypad.closeFor(viewer);
        notify(viewer, SecurityMessageKey.SECURITY_VERIFY_SUCCESS);
        rememberDevice(player, viewer);
    }

    private void fail(Player player, PlayerRef viewer, boolean reopenOnFailure) {
        int failures = sessions.recordFailure(viewer.uuid());
        LockoutPolicy policy = config.lockoutPolicy();
        if (policy.evaluate(failures) == LockoutPolicy.AttemptOutcome.LOCKED_OUT) {
            sessions.lock(viewer.uuid(), clock.instant().plus(config.lockout()));
            kick(viewer, SecurityMessageKey.SECURITY_VERIFY_LOCKED_OUT);
            return;
        }
        notify(
                viewer,
                SecurityMessageKey.SECURITY_VERIFY_FAILED,
                Map.of("remaining", Integer.toString(policy.remaining(failures))));
        if (reopenOnFailure) {
            reopenKeypad(player, viewer);
        }
    }

    private void reopenKeypad(Player player, PlayerRef viewer) {
        if (sessions.isPending(viewer.uuid())) {
            keypad.open(player, viewer, hasTotp(viewer));
        }
    }

    /** Record a device trust for the just-verified player so their next join skips the keypad (if trust is on). */
    private void rememberDevice(Player player, PlayerRef viewer) {
        if (!config.trustDevices() || config.trustDuration().isZero()) {
            return;
        }
        String ipHash = ipHash(player);
        if (ipHash == null) {
            return;
        }
        Instant until = clock.instant().plus(config.trustDuration());
        scheduler.async(() -> trustStore.trust(viewer.uuid(), ipHash, until));
    }

    private boolean hasTotp(PlayerRef viewer) {
        return repository
                .find(viewer.uuid())
                .map(TwoFactorRegistration::totpEnabled)
                .orElse(false);
    }

    private void kick(PlayerRef viewer, SecurityMessageKey key) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live != null && live.isOnline()) {
            keypad.closeFor(viewer);
            live.kick(render(viewer, key));
        }
    }

    private @Nullable String ipHash(Player player) {
        InetSocketAddress socket = player.getAddress();
        if (socket == null) {
            return null;
        }
        InetAddress address = socket.getAddress();
        return address == null ? null : IpHashing.hash(address.getHostAddress());
    }

    private void notify(PlayerRef viewer, SecurityMessageKey key) {
        notify(viewer, key, Map.of());
    }

    private void notify(PlayerRef viewer, SecurityMessageKey key, Map<String, String> placeholders) {
        sink.deliver(viewer, messages.resolve(viewer, key, placeholders));
    }

    private Component render(PlayerRef viewer, SecurityMessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }
}
