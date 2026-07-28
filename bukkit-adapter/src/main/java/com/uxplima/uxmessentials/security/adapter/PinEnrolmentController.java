package com.uxplima.uxmessentials.security.adapter;

import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.security.adapter.inbound.gui.KeypadActions;
import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadView;
import com.uxplima.uxmessentials.security.application.PinSetResult;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.security.application.SetPin;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Drives the create-a-PIN pad shown to a player the server requires a PIN from who has not got one.
 *
 * <p>Requiring a factor is only half a feature if the player then has to find a command and read its syntax to comply,
 * especially since the whole point is that they are frozen and cannot use commands. So the same keypad they will use
 * every day to prove the PIN is the one they set it on: tap it, submit, tap it again, submit again. Asking for it
 * twice is not ceremony; it is the only thing standing between a mistyped PIN and an account whose owner cannot get
 * past the keypad tomorrow.
 *
 * <p>Nothing is stored until both entries agree and the policy accepts them, so a player who gets it wrong loses the
 * attempt and not the account. A refusal (too short, too common) explains itself and puts them back at the first step
 * rather than dropping them out of a freeze they are not allowed to leave. The plaintext lives only in
 * {@link PinEnrolmentSessions} between the two taps, and the store hashes it on the way in.
 *
 * <p>The policy check and the write both go through {@link SetPin}, the same use case {@code /pin set} uses, so a PIN
 * created here is subject to exactly the rules a self-service one is. The store write runs off the tick thread and the
 * reply hops back to the player's own region thread, so the flow is Folia-safe.
 */
@NullMarked
public final class PinEnrolmentController implements KeypadActions {

    private final SetPin setPin;
    private final PinEnrolmentSessions sessions;
    private final VerificationSessions freezes;
    private final PinKeypadView keypad;
    private final VerificationFeedback feedback;
    private final FreezeGameMode gameMode;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink sink;

    public PinEnrolmentController(
            SetPin setPin,
            PinEnrolmentSessions sessions,
            VerificationSessions freezes,
            PinKeypadView keypad,
            VerificationFeedback feedback,
            FreezeGameMode gameMode,
            Scheduler scheduler,
            Messages messages,
            MessageSink sink) {
        this.setPin = Objects.requireNonNull(setPin, "setPin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.freezes = Objects.requireNonNull(freezes, "freezes");
        this.keypad = Objects.requireNonNull(keypad, "keypad");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.gameMode = Objects.requireNonNull(gameMode, "gameMode");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Put {@code viewer} at the create pad and ask them for their first entry. */
    public void begin(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        sessions.begin(viewer.uuid());
        gameMode.apply(player);
        notify(viewer, SecurityMessageKey.SECURITY_PIN_CREATE_PROMPT);
        keypad.openCreate(player, viewer);
        feedback.prompt(viewer);
    }

    @Override
    public void submit(Player player, PlayerRef viewer, String candidate) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(candidate, "candidate");
        if (!sessions.isPending(viewer.uuid())) {
            return;
        }
        sessions.firstEntry(viewer.uuid())
                .ifPresentOrElse(
                        first -> confirmAgainst(player, viewer, first, candidate),
                        () -> takeFirst(player, viewer, candidate));
    }

    /** The create pad has no authenticator handoff: there is no factor to prove against yet. */
    @Override
    public void requestTotp(Player player, PlayerRef viewer) {
        // Nothing to do. The create spec ships without the button, and a spec that adds it back does nothing.
    }

    /**
     * The first of the two entries. It is checked against the policy now rather than after the confirmation, so a
     * player who picks something the server will refuse hears it immediately instead of typing it twice first.
     */
    private void takeFirst(Player player, PlayerRef viewer, String candidate) {
        MessageKey refusal = refusalFor(candidate);
        if (refusal != null) {
            notify(viewer, refusal);
            restart(player, viewer);
            return;
        }
        sessions.rememberFirst(viewer.uuid(), candidate);
        notify(viewer, SecurityMessageKey.SECURITY_PIN_CREATE_CONFIRM);
        keypad.openCreate(player, viewer);
    }

    /** The second entry. Only a match writes anything; a mismatch costs the attempt and starts over. */
    private void confirmAgainst(Player player, PlayerRef viewer, String first, String candidate) {
        if (!first.equals(candidate)) {
            notify(viewer, SecurityMessageKey.SECURITY_PIN_CREATE_MISMATCH);
            feedback.failure(viewer, 0);
            restart(player, viewer);
            return;
        }
        scheduler.async(() -> {
            PinSetResult result = setPin.set(viewer.uuid(), candidate);
            scheduler.onEntity(viewer, () -> applyResult(player, viewer, result));
        });
    }

    private void applyResult(Player player, PlayerRef viewer, PinSetResult result) {
        if (result != PinSetResult.SET && result != PinSetResult.ALREADY_SET) {
            notify(viewer, refusalKey(result));
            restart(player, viewer);
            return;
        }
        // The PIN exists now, so the reason they were held here is gone: end the enrolment and the freeze together,
        // hand back the game mode the freeze borrowed, and let them play. They will be asked to prove it next join.
        sessions.clear(viewer.uuid());
        freezes.clear(viewer.uuid());
        keypad.closeFor(viewer);
        gameMode.restore(player);
        notify(viewer, SecurityMessageKey.SECURITY_PIN_CREATE_DONE);
        feedback.success(viewer);
    }

    /** Put the player back at the first step with a fresh pad, still held until they produce a PIN. */
    private void restart(Player player, PlayerRef viewer) {
        sessions.restart(viewer.uuid());
        keypad.openCreate(player, viewer);
    }

    /** The refusal for {@code candidate} before it is ever stored, or null when the policy accepts it. */
    private @org.jspecify.annotations.Nullable MessageKey refusalFor(String candidate) {
        PinSetResult dryRun = setPin.validate(candidate);
        return dryRun == PinSetResult.SET ? null : refusalKey(dryRun);
    }

    private static MessageKey refusalKey(PinSetResult result) {
        return switch (result) {
            case TOO_SHORT -> SecurityMessageKey.SECURITY_PIN_TOO_SHORT;
            case TOO_LONG -> SecurityMessageKey.SECURITY_PIN_TOO_LONG;
            case NOT_NUMERIC -> SecurityMessageKey.SECURITY_PIN_NOT_NUMERIC;
            case BLOCKED -> SecurityMessageKey.SECURITY_PIN_BLOCKED;
            case SET, ALREADY_SET -> SecurityMessageKey.SECURITY_PIN_CREATE_DONE;
        };
    }

    private void notify(PlayerRef viewer, MessageKey key) {
        sink.deliver(viewer, messages.resolve(viewer, key, Map.of()));
    }
}
