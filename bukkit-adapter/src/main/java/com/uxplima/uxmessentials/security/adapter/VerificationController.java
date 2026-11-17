package com.uxplima.uxmessentials.security.adapter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.security.adapter.inbound.gui.KeypadActions;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadView;
import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.SessionCarry;
import com.uxplima.uxmessentials.security.application.VerifyResult;
import com.uxplima.uxmessentials.security.application.port.LockoutBan;
import com.uxplima.uxmessentials.security.application.port.TrustStore;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.SafetyNet;
import com.uxplima.uxmessentials.security.domain.SecurityMessageKey;
import com.uxplima.uxmessentials.security.domain.event.AccountLockedOut;
import com.uxplima.uxmessentials.security.domain.event.VerificationFailed;
import com.uxplima.uxmessentials.security.domain.event.VerificationPassed;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ServerConnector;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The brain of the join-verification freeze: it decides on join whether a player must prove their second factor,
 * freezes them by marking a {@link VerificationSessions} entry, and drives the outcome of every submitted PIN or code
 * through to an unfreeze, a re-prompt, or a lockout kick. The keypad and the listeners are the hands: this class holds
 * the judgement the GUI has no business knowing.
 *
 * <p>On a network a proof carries to the next backend the player switches to (see {@link SessionCarry}): a proof is
 * recorded when it is made, released when the player leaves, and a join that carries one lifts the freeze unprompted.
 *
 * <p>Every DB read (the registration, the device-trust check, the attempt record, the carried session) and write
 * (recording a trust, counting an attempt) runs off the tick thread
 * through the injected {@link Scheduler}, and every player touch (the prompt, the keypad, the kick) hops back onto the
 * player's region thread, so the flow is Folia-safe and never blocks a tick on I/O. A submitted PIN or code is held
 * only for the length of the verify and is never logged.
 */
@NullMarked
public final class VerificationController implements KeypadActions {

    /**
     * Holding this node means the server requires a PIN of you: joining without one holds you at the create pad
     * rather than letting you through. It ships denied, so nothing changes until an operator grants it.
     */
    public static final String PIN_REQUIRED_PERMISSION = EnforcementPermissions.SECURITY_PIN_REQUIRED;

    private final TwoFactorRepository repository;
    private final ProofChecks checks;
    private final TrustStore trustStore;
    private final IpTokens ipHashing;
    private final VerificationSessions sessions;
    private final AttemptLimiter limiter;
    private final ReauthState reauthState;
    private final SecurityConfig.JoinVerification config;
    private final PinKeypadView keypad;
    private final TotpPrompt totpPrompt;
    private final FreezeGameMode gameMode;
    private final VerificationFeedback feedback;
    private final LockoutBan lockoutBan;
    private final PinEnrolmentController enrolment;
    private final Permissions permissions;
    private final FreezeHoldingArea holdingArea;
    private final ServerConnector proxy;
    private final DomainEventPublisher events;
    private final SessionCarry carry;
    private final ProxyArrivals arrivals;
    private final String serverId;
    private final NetworkPresence presence;
    private final PlayerLane lane;

    /**
     * Set once at startup when a login plugin is hooked. It is written on the main thread during wiring and read by
     * the join hook afterwards, so it is deliberately a plain field rather than shared mutable state.
     */
    private boolean deferredToLoginPlugin;

    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink sink;
    private final Logger log;
    private final Clock clock;

    public VerificationController(
            TwoFactorRepository repository,
            ProofChecks checks,
            TrustStore trustStore,
            VerificationSessions sessions,
            AttemptLimiter limiter,
            ReauthState reauthState,
            SecurityConfig.JoinVerification config,
            PinKeypadView keypad,
            TotpPrompt totpPrompt,
            FreezeGameMode gameMode,
            VerificationFeedback feedback,
            LockoutBan lockoutBan,
            PinEnrolmentController enrolment,
            Permissions permissions,
            FreezeHoldingArea holdingArea,
            ServerConnector proxy,
            IpTokens ipHashing,
            DomainEventPublisher events,
            SessionCarry carry,
            ProxyArrivals arrivals,
            String serverId,
            NetworkPresence presence,
            PlayerLane lane,
            Scheduler scheduler,
            Messages messages,
            MessageSink sink,
            Logger log,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.checks = Objects.requireNonNull(checks, "checks");
        this.trustStore = Objects.requireNonNull(trustStore, "trustStore");
        this.ipHashing = Objects.requireNonNull(ipHashing, "ipHashing");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        this.reauthState = Objects.requireNonNull(reauthState, "reauthState");
        this.config = Objects.requireNonNull(config, "config");
        this.keypad = Objects.requireNonNull(keypad, "keypad");
        this.totpPrompt = Objects.requireNonNull(totpPrompt, "totpPrompt");
        this.gameMode = Objects.requireNonNull(gameMode, "gameMode");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.lockoutBan = Objects.requireNonNull(lockoutBan, "lockoutBan");
        this.enrolment = Objects.requireNonNull(enrolment, "enrolment");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.holdingArea = Objects.requireNonNull(holdingArea, "holdingArea");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.events = Objects.requireNonNull(events, "events");
        this.carry = Objects.requireNonNull(carry, "carry");
        this.arrivals = Objects.requireNonNull(arrivals, "arrivals");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Freeze {@code player} synchronously on join, then decide off the tick thread whether the freeze must stay. The
     * pending flag is set before the async enrolment lookup runs, so a queued command/chat/interaction packet fired in
     * the join window is already cancelled by the freeze listeners, "frozen until proven safe". The async decision
     * clears the freeze again for players who turn out to be not-enrolled, on a trusted device, or locked out.
     */
    public void onJoin(Player player) {
        Objects.requireNonNull(player, "player");
        if (!config.enabled()) {
            return;
        }
        // The join begins here, even when a login plugin decides it later: what the proxy said before it is stale.
        arrivals.joined(player.getUniqueId());
        if (deferredToLoginPlugin) {
            // An offline-mode server's login plugin has not authenticated them yet, so there is no account to ask a
            // second factor of. onAuthenticated picks this up the moment there is one.
            return;
        }
        beginJoinDecision(player);
    }

    private void beginJoinDecision(Player player) {
        PlayerRef ref = BukkitRefs.toRef(player);
        // Put back a game mode a previous freeze borrowed and never handed back, before anything reads it: the one
        // case that happens in is the server going down mid-verification, and the stamp is still on the player.
        gameMode.restore(player);
        long token = sessions.begin(ref.uuid());
        String ipHash = ipHash(player);
        scheduler.async(() -> guarded(ref, () -> decideJoin(ref, ipHash, token)));
    }

    /**
     * Start the join decision for a player a login plugin has just authenticated. This is the offline-mode entry
     * point: with a login plugin installed the ordinary join hook stands down, because a connecting client on a
     * cracked server has proved nothing and the name is whatever they typed. Password first, second factor second.
     */
    public void onAuthenticated(Player player) {
        Objects.requireNonNull(player, "player");
        if (!config.enabled()) {
            return;
        }
        beginJoinDecision(player);
    }

    /** Tell the controller a login plugin owns the first half of the login, so the plain join hook stands down. */
    public void deferToLoginPlugin() {
        this.deferredToLoginPlugin = true;
    }

    /** Drop a leaving player's freeze so a disconnect mid-verification leaves no lingering pending entry. */
    public void onQuit(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        sessions.clear(viewer.uuid());
        arrivals.left(viewer.uuid());
        if (carry.enabled()) {
            Instant now = clock.instant();
            lane.run(viewer.uuid(), "security_session_release_failed", () -> carry.left(viewer.uuid(), serverId, now));
        }
        // A half-made PIN must not outlive the session it was being made in: kept, it would take the next join's
        // entries as a new PIN rather than as proof of the one that exists by then.
        enrolment.abandon(viewer.uuid());
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live != null) {
            gameMode.restore(live);
            // A disconnect mid-verification must not leave them logged out inside the holding area.
            holdingArea.release(live);
        }
    }

    /**
     * Run a join decision so that a failure inside it cannot leave the player frozen forever. The freeze is applied
     * optimistically before the decision runs, which is what makes a queued packet in the join window safe; the price
     * is that a decision which throws (the database is unreachable, the key-file is unreadable) would otherwise strand
     * the player with no keypad, no message and nothing to press. The configured {@link SafetyNet} settles it: kick
     * them with a "try again" line, or lift the freeze and let them in unverified. Either way the failure is logged
     * once with the player it happened to, because a silent outage here looks exactly like a hung server.
     */
    private void guarded(PlayerRef ref, Runnable decision) {
        try {
            decision.run();
        } catch (RuntimeException failure) {
            log.error(
                    "event=security_verify_decision_failed player=" + ref.name() + " policy=" + config.safetyNet(),
                    failure);
            sessions.clear(ref.uuid());
            if (config.safetyNet() == SafetyNet.KICK) {
                scheduler.onEntity(ref, () -> kick(ref, SecurityMessageKey.SECURITY_VERIFY_UNAVAILABLE));
            }
        }
    }

    /**
     * Force {@code ref} back into the freeze now, driving the immediate half of {@code /2fa force}. It begins a
     * session and, off the tick thread, re-reads the registration and opens the keypad on the player's region thread
     * like a fresh join, but without the trusted-device bypass, so even a trusted device is re-prompted. It is a
     * no-op when join verification is disabled, and self-clearing for a target who holds no factor or has already
     * gone offline (the freeze is lifted rather than left hanging). The durable part (revoking device trust so the
     * next join re-verifies too) is set by the {@code ForceReverification} use case before this is called.
     */
    public void forceReverify(PlayerRef ref) {
        Objects.requireNonNull(ref, "ref");
        if (!config.enabled()) {
            return;
        }
        long token = sessions.begin(ref.uuid());
        scheduler.async(() -> guarded(ref, () -> forceDecide(ref, token)));
    }

    /**
     * Apply the operator's revoked-access policy to {@code ref}, whose second factor staff have just cleared.
     *
     * <p>A reset is the recovery door, and what it should do to a player standing in the world at that moment is a
     * judgement about the server, not about the code: routine help-desk work on one server, the aftermath of a
     * suspected theft on another. So the module does what it was told and nothing more, and the default is the middle
     * answer, sending them back through verification so a reset ends with a factor rather than without one.
     */
    public void onAccessRevoked(PlayerRef ref) {
        Objects.requireNonNull(ref, "ref");
        switch (config.revokedAccess()) {
            case NOTHING -> {
                // Deliberately nothing: the operator has said a reset does not interrupt the session it lands in.
            }
            case REVERIFY -> forceReverify(ref);
            case KICK -> scheduler.onEntity(ref, () -> kick(ref, SecurityMessageKey.SECURITY_ACCESS_REVOKED));
        }
    }

    private void forceDecide(PlayerRef ref, long token) {
        TwoFactorRegistration registration = repository.find(ref.uuid()).orElse(null);
        if (registration == null || !registration.hasAnyFactor()) {
            // Nothing to prove, so normally the freeze lifts. The exception is a player the server requires a PIN
            // from: this is the path an operator's `/security reset` takes, and it is exactly when they should be
            // made to set a new one rather than quietly left with no factor at all.
            if (permissions.has(ref, PIN_REQUIRED_PERMISSION)) {
                scheduler.onEntity(ref, () -> beginEnrolment(ref, token));
                return;
            }
            sessions.clear(ref.uuid());
            // A forced re-verify can land while a prompt is already up; with nothing left to prove it must go too,
            // or it would sit there asking for an answer nothing waits on.
            keypad.closeIfAsking(ref);
            return;
        }
        boolean totpEnabled = registration.totpEnabled();
        scheduler.onEntity(ref, () -> beginFreeze(ref, totpEnabled, token));
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

    private void decideJoin(PlayerRef ref, @Nullable String ipHash, long token) {
        Instant now = clock.instant();
        if (limiter.isLockedOut(ref.uuid(), now)) {
            sessions.clear(ref.uuid());
            scheduler.onEntity(ref, () -> kick(ref, SecurityMessageKey.SECURITY_VERIFY_LOCKED_OUT));
            return;
        }
        TwoFactorRegistration registration = repository.find(ref.uuid()).orElse(null);
        if (registration == null || !registration.hasAnyFactor()) {
            // Not enrolled. Normally that lifts the optimistic freeze; but a player the server requires a PIN from is
            // held instead and made to set one now, because "you must have a factor" means nothing if not having one
            // is the way past it.
            if (permissions.has(ref, PIN_REQUIRED_PERMISSION)) {
                scheduler.onEntity(ref, () -> beginEnrolment(ref, token));
                return;
            }
            sessions.clear(ref.uuid());
            return;
        }
        if (config.trustDevices() && ipHash != null && trustStore.isTrusted(ref.uuid(), ipHash, now)) {
            sessions.clear(ref.uuid()); // a trusted device skips the prompt, lift the optimistic freeze
            return;
        }
        boolean totpEnabled = registration.totpEnabled();
        if (carry.holds(ref.uuid())) {
            // A proof made elsewhere on the network may carry here; how the player arrived decides, and the proxy may
            // still be on its way to say so.
            var unused = arrivals.arrival(ref.uuid())
                    .thenAccept(arrival -> lane.run(
                            ref.uuid(),
                            "security_verify_decision_failed",
                            () -> guarded(ref, () -> carryOrFreeze(ref, ipHash, token, totpEnabled, arrival))));
            return;
        }
        scheduler.onEntity(ref, () -> beginFreeze(ref, totpEnabled, token));
    }

    /** Lift the freeze when the player's proof carries to this backend, or hold them at the keypad. */
    private void carryOrFreeze(
            PlayerRef ref, @Nullable String ipHash, long token, boolean totpEnabled, SessionCarry.Arrival arrival) {
        if (!sessions.isPending(ref.uuid(), token)) {
            return;
        }
        boolean carried = carry.carries(
                ref.uuid(),
                serverId,
                arrival,
                Optional.ofNullable(ipHash),
                clock.instant(),
                server -> presence.onServer(server).stream()
                        .anyMatch(online -> online.player().uuid().equals(ref.uuid())));
        if (!carried) {
            scheduler.onEntity(ref, () -> beginFreeze(ref, totpEnabled, token));
            return;
        }
        if (!sessions.isPending(ref.uuid(), token)) {
            // They left while the session was taken: let it go again, as their quit would have.
            carry.left(ref.uuid(), serverId, clock.instant());
            return;
        }
        sessions.clear(ref.uuid());
    }

    /**
     * Hold {@code ref} at the create-a-PIN pad; the freeze stays on until they have one, or until the entry timeout
     * runs out, exactly as it would at the verify pad.
     */
    private void beginEnrolment(PlayerRef ref, long token) {
        Player live = Bukkit.getPlayer(ref.uuid());
        if (live == null || !live.isOnline()) {
            sessions.clear(ref.uuid());
            return;
        }
        enrolment.begin(live, ref);
        startEntryTimeout(ref, token);
    }

    private void beginFreeze(PlayerRef ref, boolean totpEnabled, long token) {
        Player live = Bukkit.getPlayer(ref.uuid());
        if (live == null || !live.isOnline()) {
            sessions.clear(ref.uuid());
            return;
        }
        // They hold a factor now, so this freeze is answered by proving it; a create-a-PIN session left from before
        // must not be the one that takes their entries.
        enrolment.abandon(ref.uuid());
        // A spectator cannot click any window the server opens, so move them somewhere they can before the keypad
        // goes up; the mode they had is put back the moment they verify or leave.
        gameMode.apply(live);
        // Optional, and off by default: park the unverified session somewhere deliberately empty for the length of
        // the freeze, and hand back where they were once they prove who they are.
        holdingArea.hold(live);
        notify(ref, SecurityMessageKey.SECURITY_VERIFY_PROMPT);
        keypad.open(live, ref, totpEnabled);
        feedback.prompt(ref);
        startEntryTimeout(ref, token);
    }

    /**
     * Arm the time limit on this freeze. Without one a frozen player simply sits at the keypad forever, holding a
     * player slot and, on a full server, keeping somebody else out; and an account whose owner walked away mid-prompt
     * stays logged in indefinitely, which is the opposite of what the freeze is for.
     *
     * <p>The token is what keeps this honest. The timer fires long after the freeze that armed it, by which point the
     * player may have verified, left, rejoined and been frozen again; comparing tokens means a stale timer sees a
     * freeze it was not started for and does nothing, rather than kicking someone out of a perfectly good session.
     */
    private void startEntryTimeout(PlayerRef ref, long token) {
        if (!config.hasEntryTimeout()) {
            return;
        }
        scheduler.asyncAfter(config.entryTimeout(), () -> {
            if (!sessions.isPending(ref.uuid(), token)) {
                return;
            }
            sessions.clear(ref.uuid());
            scheduler.onEntity(ref, () -> kick(ref, SecurityMessageKey.SECURITY_VERIFY_TIMED_OUT));
        });
    }

    private void verifySubmission(Player player, PlayerRef viewer, String candidate, boolean reopenOnFailure) {
        OptionalLong token = sessions.token(viewer.uuid());
        if (token.isEmpty()) {
            return;
        }
        scheduler.async(() -> {
            // The shared budget is a database record, so the proof is checked here, off the tick, before the answer
            // goes back to the player's thread. One check runs per player; a submission made meanwhile is dropped.
            ProofChecks.Checked checked = checks.check(viewer.uuid(), candidate);
            scheduler.onEntity(
                    viewer, () -> applyResult(player, viewer, checked, token.getAsLong(), reopenOnFailure), () -> {
                        // They are gone before the answer reached them; a lockout is still put on record.
                        if (checked.status() == ProofChecks.Status.LOCKED_NOW) {
                            scheduler.onGlobal(() -> recordLockout(viewer));
                        }
                    });
        });
    }

    private void applyResult(
            Player player, PlayerRef viewer, ProofChecks.Checked checked, long token, boolean reopenOnFailure) {
        boolean pending = sessions.isPending(viewer.uuid(), token);
        boolean lockedNow = checked.status() == ProofChecks.Status.LOCKED_NOW;
        boolean lockedOut = lockedNow
                || (pending
                        && (checked.status() == ProofChecks.Status.ALREADY_LOCKED
                                || limiter.knownLockedOut(viewer.uuid(), clock.instant())));
        if (lockedOut) {
            // A lockout this proof set is put on record even when its freeze is over; one set before is on record.
            sessions.clear(viewer.uuid());
            lockOut(viewer, lockedNow);
            return;
        }
        if (!pending) {
            // The freeze this proof was for is over: they left, timed out, or were frozen anew meanwhile.
            return;
        }
        switch (checked.status()) {
            case PASSED -> succeed(player, viewer, checked.result() == VerifyResult.SUCCESS);
            case WRONG -> fail(player, viewer, new AttemptLimiter.Outcome(false, checked.remaining()), reopenOnFailure);
            case UNAVAILABLE -> {
                notify(viewer, SecurityMessageKey.SECURITY_VERIFY_UNAVAILABLE);
                askAgain(player, viewer, reopenOnFailure);
            }
            // The check already running answers too; a typed entry is gone once submitted, so this one asks again.
            case BUSY -> askAgain(player, viewer, reopenOnFailure);
            case LOCKED_NOW, ALREADY_LOCKED -> {
                // Handled above.
            }
        }
    }

    private void succeed(Player player, PlayerRef viewer, boolean proved) {
        sessions.clear(viewer.uuid());
        gameMode.restore(player);
        // A fresh join proof also opens the op-command re-auth window, so the player is not re-asked to verify to run
        // a protected command they were just about to run.
        reauthState.stamp(viewer.uuid(), clock.instant());
        keypad.closeFor(viewer);
        holdingArea.release(player);
        notify(viewer, SecurityMessageKey.SECURITY_VERIFY_SUCCESS);
        feedback.success(viewer);
        rememberDevice(player, viewer);
        // Only a real proof is a pass. A player who turned out to hold no factor was never asked for one, and
        // saying they proved something would be untrue.
        if (proved) {
            events.publish(new VerificationPassed(viewer));
            rememberSession(player, viewer);
        }
        transferOnward(player);
    }

    private void fail(Player player, PlayerRef viewer, AttemptLimiter.Outcome outcome, boolean reopenOnFailure) {
        if (outcome.lockedOut()) {
            sessions.clear(viewer.uuid());
            lockOut(viewer, true);
            return;
        }
        events.publish(new VerificationFailed(viewer, outcome.remaining()));
        notify(
                viewer,
                SecurityMessageKey.SECURITY_VERIFY_FAILED,
                Map.of("remaining", Integer.toString(outcome.remaining())));
        feedback.failure(viewer, outcome.remaining());
        if (reopenOnFailure) {
            reopenKeypad(player, viewer);
        } else {
            keypad.retry(player, viewer);
        }
    }

    /**
     * End a lockout. When the operator has asked for it and there is a ban surface to write to, the lockout is issued
     * as an ordinary tempban through the plugin's own ban system, so it survives a restart, appears in the punishment
     * history and is lifted with {@code /unban} like anything else, rather than this module keeping a private ban list
     * nobody can see. That path kicks the player itself; only the in-memory fallback has to kick here.
     */
    private void lockOut(PlayerRef viewer, boolean record) {
        keypad.closeFor(viewer);
        if (record && recordLockout(viewer)) {
            return;
        }
        kick(viewer, SecurityMessageKey.SECURITY_VERIFY_LOCKED_OUT);
    }

    /** Put a lockout on record: the ban the operator asked for, and the event. Whether the ban was written. */
    private boolean recordLockout(PlayerRef viewer) {
        boolean banned = config.lockoutBans() && lockoutBan.ban(viewer, config.lockout(), config.lockoutBanReason());
        events.publish(new AccountLockedOut(viewer, config.lockout(), banned));
        return banned;
    }

    /** Ask for the entry again: the keypad after an authenticator prompt, or the typed prompt a submission ended. */
    private void askAgain(Player player, PlayerRef viewer, boolean reopenOnFailure) {
        if (reopenOnFailure) {
            reopenKeypad(player, viewer);
        } else {
            keypad.retry(player, viewer);
        }
    }

    /**
     * Bring the keypad back after the authenticator prompt: on its dismissal, or after a code it took was refused. That
     * prompt is reached only through the keypad's authenticator button, which shows only for a player with an
     * authenticator, so the button is shown again without reading the registration, which would block the player's
     * thread on the database.
     */
    private void reopenKeypad(Player player, PlayerRef viewer) {
        if (sessions.isPending(viewer.uuid())) {
            keypad.open(player, viewer, true);
        }
    }

    /**
     * Hand a verified player to another backend, when the operator has named one. This is what a dedicated
     * authentication server is for: the whole of a player's unverified session happens on a lobby that has nothing on
     * it worth reaching, and only a proved account ever lands on the server that does. A blank target, or a backend
     * with no outgoing proxy channel registered, leaves them where they are.
     */
    private void transferOnward(Player player) {
        if (!config.hasTransferTarget() || !proxy.isAvailable()) {
            return;
        }
        proxy.connect(player, config.transferTo());
    }

    /** Record the proof so the player keeps it when they switch to another backend of the network. */
    private void rememberSession(Player player, PlayerRef viewer) {
        if (!carry.enabled()) {
            return;
        }
        Optional<String> ipHash = Optional.ofNullable(ipHash(player));
        Optional<UUID> connection = arrivals.connection(viewer.uuid());
        Instant now = clock.instant();
        lane.run(
                viewer.uuid(),
                "security_session_record_failed",
                () -> carry.proved(viewer.uuid(), serverId, connection, ipHash, now));
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

    private void kick(PlayerRef viewer, SecurityMessageKey key) {
        enrolment.abandon(viewer.uuid());
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live != null && live.isOnline()) {
            keypad.closeFor(viewer);
            gameMode.restore(live);
            live.kick(render(viewer, key));
        }
    }

    private @Nullable String ipHash(Player player) {
        InetSocketAddress socket = player.getAddress();
        if (socket == null) {
            return null;
        }
        InetAddress address = socket.getAddress();
        return address == null ? null : ipHashing.tokenFor(address.getHostAddress());
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
