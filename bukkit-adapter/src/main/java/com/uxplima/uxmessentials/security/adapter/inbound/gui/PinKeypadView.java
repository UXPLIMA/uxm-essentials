package com.uxplima.uxmessentials.security.adapter.inbound.gui;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.security.adapter.PinEntryPrompt;
import com.uxplima.uxmessentials.security.adapter.SecureEntryInput;
import com.uxplima.uxmessentials.security.adapter.VerificationFeedback;
import com.uxplima.uxmessentials.security.domain.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.EngineLog;
import com.uxplima.uxmessentials.shared.adapter.outbound.LivePlayers;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.MenuSpecs;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The join-verification / op-command re-auth keypad, rendered through the menu engine: the numbered-head pad a frozen
 * player taps their PIN or authenticator code into, laid out 0-4 over 5-9 so every digit including zero sits in one
 * even grid. The engine cancels every click and drag on the window, so the pad is input-only and no item can move;
 * each button runs one of this view's registered {@code security:pin-*} actions.
 * This view owns the per-viewer entered-PIN buffer (carried on the open menu as its subject) and the verify handoff:
 * a digit appends and re-renders the masked display, clear empties it, submit reads and verifies it, and the code
 * button hands off to the text-input prompt. The verify/lockout/trust decision lives behind {@link KeypadActions}.
 *
 * <p>Security is preserved end to end: the engine's blanket click/drag cancel is the pad's lock; the entered digits
 * live only in the {@link PinSession} subject server-side and never leave as a component (only a mask of asterisks is
 * ever rendered); and while a player is still frozen the {@link PinKeypadWindowListener} reopens the window on any
 * escape, so verification cannot be skipped. A deliberate close (a successful verify, a lockout kick, the TOTP
 * handoff, or a module stop) is flagged through {@link #suppressNextClose} / {@link #closeFor} so it is not fought.
 * Every live-player touch runs on the viewer's own region thread through the engine and the injected {@link Scheduler},
 * so it stays Folia-safe.
 *
 * <p>A player the {@link PinEntryPrompt} says types their entry (a Bedrock player) never sees the window. A form is sent
 * once and gone after one button, so a pad that redraws after each tap would close on the first digit and leave them
 * frozen with nothing to press. They are asked for the whole entry in one prompt instead, and the rest of this view
 * treats that prompt as it treats the window: a submission goes to the same {@link KeypadActions}, a refused one is
 * asked again ({@link #retry}), a close while still frozen asks again, and a deliberate close drops it. No open is in
 * flight for such a prompt, so nothing waits on an inventory event that a form never raises.
 */
@NullMarked
public final class PinKeypadView {

    /** The engine spec id this keypad registers and opens under. */
    public static final String SPEC_ID = "pin-keypad";

    /** The engine spec id of the create-a-PIN pad: the same buttons, its own title and no authenticator handoff. */
    public static final String CREATE_SPEC_ID = "pin-create";

    /** Whether {@code specId} belongs to either security keypad surface. */
    public static boolean isKeypadSpec(String specId) {
        return SPEC_ID.equals(specId) || CREATE_SPEC_ID.equals(specId);
    }

    private static final String SPEC_RESOURCE = "modules/security/gui/pin-keypad.conf";

    private static final String CREATE_SPEC_RESOURCE = "modules/security/gui/pin-create.conf";

    /** The fallback row count when the spec cannot be read: enough for the entry row, the two digit rows and the controls. */
    private static final int ROWS = 4;

    /** The most digits the pad accepts, comfortably above an 8-digit PIN and a 6-digit code. */
    private static final int MAX_ENTRY = 12;

    /** A typed prompt gone sooner than this was most likely never on the player's screen at all. */
    private static final Duration QUICK_CLOSE = Duration.ofSeconds(1);

    /** The wait before asking again after the first quick close; it doubles with each one in a row. */
    private static final Duration FIRST_BACKOFF = Duration.ofMillis(500);

    /** The longest wait between two asks. The freeze's entry timeout still ends the whole thing. */
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(4);

    /** How many quick closes in a row are a prompt that cannot be shown rather than a player in a hurry. */
    private static final int STREAK_WORTH_LOGGING = 3;

    private final Menus menus;
    private final PinEntryPrompt typed;
    private final Predicate<UUID> frozen;
    private final Messages messages;
    private final VerificationFeedback feedback;
    private final Scheduler scheduler;
    private final Logger log;

    /** viewer UUID -> their open keypad's viewer + totp flag; the tracking that lets closeAll close and reopen rebuild. */
    private final Map<UUID, Tracked> open = new ConcurrentHashMap<>();

    /** Viewers whose next keypad close is a deliberate handoff or teardown, so it must not reopen. */
    private final Set<UUID> suppressReopen = ConcurrentHashMap.newKeySet();

    /**
     * How many opens this view has asked the engine for per viewer and not yet seen land. An engine open is not
     * synchronous: it hops off the tick thread to resolve the spec and back to the viewer's entity thread to show the
     * window, so the {@code openInventory} that ends it happens long after {@link #show} returns. Opening a window
     * over an open one makes the server close the old one first, and that close is an ordinary
     * {@link org.bukkit.event.inventory.InventoryCloseEvent} carrying this very keypad: without this counter the
     * listener reads it as the player escaping, reopens, and the reopen fires the next close. The counter is raised
     * before the open is asked for and lowered by {@link #openLanded} when the window actually appears, so the whole
     * in-flight window is covered rather than only the synchronous part of it.
     */
    private final Map<UUID, Integer> inFlight = new ConcurrentHashMap<>();

    /**
     * viewer UUID -> the token of the typed prompt they are answering. A callback carrying any other token belongs to
     * a prompt this view has since dropped (a deliberate close, a quit, a module stop) and is ignored, so a form the
     * player answers late can neither submit nor reopen anything.
     */
    private final Map<UUID, Long> typing = new ConcurrentHashMap<>();

    private final AtomicLong promptTokens = new AtomicLong();

    /** viewer UUID -> when their open typed prompt was asked for, from {@link System#nanoTime}. */
    private final Map<UUID, Long> askedAt = new ConcurrentHashMap<>();

    /** viewer UUID -> how many of their typed prompts in a row closed within {@link #QUICK_CLOSE}. */
    private final Map<UUID, Integer> quickCloses = new ConcurrentHashMap<>();

    /** Whether the backoff has been logged yet; once is enough to point an operator at the input mode. */
    private final AtomicBoolean backoffLogged = new AtomicBoolean();

    /** Where a typed entry goes; set by {@link #register}, because the router is built after this view. */
    private volatile @Nullable KeypadActions actions;

    /**
     * @param typed which players type their entry instead of tapping the window, and how they are asked
     * @param frozen whether a player is still held by the join freeze, so a close of their entry has to ask again
     */
    public PinKeypadView(
            Menus menus,
            PinEntryPrompt typed,
            Predicate<UUID> frozen,
            Messages messages,
            VerificationFeedback feedback,
            Scheduler scheduler,
            Logger log) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.typed = Objects.requireNonNull(typed, "typed");
        this.frozen = Objects.requireNonNull(frozen, "frozen");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Register the keypad's per-button actions, its masked-display / digit-label placeholders, and the spec itself. */
    public void register(MenuBindings bindings, KeypadActions actions, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        this.actions = actions;
        bindings.placeholder("security_keypad_entry", this::entryDisplay);
        for (int value = 0; value <= 9; value++) {
            int digit = value;
            bindings.placeholder("security_keypad_digit_" + digit, ctx -> digitLabel(ctx, digit));
            bindings.action("security:pin-" + digit, ctx -> pressDigit(ctx, digit));
        }
        bindings.action("security:pin-clear", this::pressClear);
        bindings.action("security:pin-submit", ctx -> pressSubmit(ctx, actions));
        bindings.action("security:pin-totp", ctx -> pressTotp(ctx, actions));
        bindings.condition("security:totp-enabled", (ctx, args) -> totpEnabled(ctx));
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, EngineLog.of(log)));
        menus.registerSpec(
                CREATE_SPEC_ID, MenuSpecs.loadOrBundled(CREATE_SPEC_RESOURCE, dataFolder, ROWS, EngineLog.of(log)));
    }

    /** Open a fresh keypad for {@code viewer}; {@code totpEnabled} shows the "type a code" button. */
    public void open(Player player, PlayerRef viewer, boolean totpEnabled) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        show(viewer, totpEnabled);
    }

    /**
     * Open the create-a-PIN pad for {@code viewer}, with an empty entry. Tracked like the verify pad so an escape is
     * reopened and a module stop closes it; it never shows the authenticator button, because there is no factor to
     * hand off to yet.
     */
    public void openCreate(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        openCreate(player, viewer, false);
    }

    private void openCreate(Player player, PlayerRef viewer, boolean confirming) {
        if (typed.typesEntry(player)) {
            askTyped(player, new Tracked(viewer, false, true, confirming, true));
            return;
        }
        open.put(viewer.uuid(), new Tracked(viewer, false, true, false, false));
        markOpening(viewer.uuid());
        menus.open(player, CREATE_SPEC_ID, new PinSession(viewer, false));
    }

    /**
     * Put {@code viewer} at the create pad only if they are not already there. The enrolment flow moves between its
     * steps without leaving the window: the submit action clears the entry and redraws the pad in place, so the next
     * step is already on screen and opening a second one would only tear down a window to build the same window
     * again. An open already in flight counts as being there, so two steps in quick succession still open one pad.
     *
     * <p>A typed entry is gone once it is submitted, so a player who types is asked again here, for the step named by
     * {@code confirming}: the same PIN again, or a fresh first entry.
     */
    public void ensureCreateOpen(Player player, PlayerRef viewer, boolean confirming) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        UUID id = viewer.uuid();
        if (typed.typesEntry(player)) {
            askTyped(player, new Tracked(viewer, false, true, confirming, true));
            return;
        }
        if (inFlight.containsKey(id)
                || menus.currentMenu(id)
                        .filter(info -> CREATE_SPEC_ID.equals(info.specId()))
                        .isPresent()) {
            return;
        }
        openCreate(player, viewer, false);
    }

    /**
     * Ask {@code viewer} again after a submission was refused. The keypad window is still on screen with an empty
     * entry, so for a keypad player this does nothing; a typed entry is gone once submitted, so it is asked for again.
     */
    public void retry(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Tracked tracked = open.get(viewer.uuid());
        if (tracked != null && tracked.typed()) {
            askTyped(player, tracked);
        }
    }

    /** Close the viewer's keypad, flagging the close so it is not reopened; the deliberate-close path (the close
     * listener drops the tracking once the flagged close lands). */
    public void closeFor(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        UUID id = viewer.uuid();
        Tracked tracked = open.get(id);
        if (tracked != null && tracked.typed()) {
            // Dropping the token is what ends a typed prompt: whatever the player answers later is ignored. An anvil
            // or sign prompt is also taken off their screen; a form or a chat prompt has nothing to close.
            boolean asking = typing.remove(id) != null;
            forget(id);
            if (asking) {
                scheduler.onEntity(viewer, () -> closeLive(id));
            }
            return;
        }
        suppressReopen.add(id);
        scheduler.onEntity(viewer, () -> closeLive(id));
    }

    /**
     * Whether {@code viewer} is being asked for a PIN or code right now: a keypad window or a typed prompt is up, or
     * one is about to be asked again after a refused or dismissed entry.
     */
    public boolean isEntering(UUID viewer) {
        return open.containsKey(Objects.requireNonNull(viewer, "viewer"));
    }

    /** Close every open keypad and drop all tracking, so a module disable leaves no locked window. */
    public void closeAll() {
        for (Tracked tracked : List.copyOf(open.values())) {
            closeFor(tracked.viewer());
        }
        open.clear();
        suppressReopen.clear();
        inFlight.clear();
        typing.clear();
        askedAt.clear();
        quickCloses.clear();
    }

    /** Flag the viewer's next keypad close as a deliberate handoff (to the TOTP prompt), so it is not reopened. */
    public void suppressNextClose(PlayerRef viewer) {
        suppressReopen.add(Objects.requireNonNull(viewer, "viewer").uuid());
    }

    /** Consume the suppress flag for {@code viewer}: true (and cleared) when the close was a deliberate handoff. */
    boolean consumeSuppress(UUID viewer) {
        return suppressReopen.remove(viewer);
    }

    /**
     * Reopen the keypad for a still-frozen viewer who escaped their window; a fresh window with an empty entry, keeping
     * the viewer's tracked totp flag. The caller checks {@link #hasOpenInFlight} first, so a close this view's own
     * pending open caused never reaches here.
     */
    void reopen(UUID viewer) {
        Tracked tracked = open.get(viewer);
        if (tracked == null) {
            return;
        }
        Player live = Bukkit.getPlayer(viewer);
        if (tracked.typed() && live != null) {
            askTyped(live, tracked);
            return;
        }
        if (tracked.creating() && live != null) {
            openCreate(live, tracked.viewer(), false);
            return;
        }
        show(tracked.viewer(), tracked.totpEnabled());
    }

    /**
     * Settle a keypad entry that has gone off the player's screen, whether a window they closed or a typed prompt they
     * dismissed. A deliberate close is left alone, a player still frozen is asked again so verification cannot be
     * skipped, and any other close (a re-auth abandoned, a quit) drops the tracking.
     */
    void closed(UUID viewer) {
        if (consumeSuppress(viewer)) {
            forget(viewer);
            return;
        }
        if (frozen.test(viewer)) {
            reopen(viewer);
        } else {
            forget(viewer);
        }
    }

    /**
     * Whether an open this view asked for has not yet put its window on the viewer's screen. A keypad close arriving
     * while that is true is the server closing the old window to make room for the new one, not the player leaving.
     */
    boolean hasOpenInFlight(UUID viewer) {
        return inFlight.containsKey(viewer);
    }

    /** One of this view's opens has landed: the window is on screen, so it is no longer in flight. */
    void openLanded(UUID viewer) {
        inFlight.computeIfPresent(viewer, (id, count) -> count <= 1 ? null : count - 1);
    }

    /** Drop the tracking for a keypad that closed without needing a reopen (a deliberate close, a re-auth escape, a quit). */
    void forget(UUID viewer) {
        open.remove(viewer);
        inFlight.remove(viewer);
        typing.remove(viewer);
        askedAt.remove(viewer);
        quickCloses.remove(viewer);
    }

    private void show(PlayerRef viewer, boolean totpEnabled) {
        Player live = LivePlayers.of(viewer).orElse(null);
        if (live != null && typed.typesEntry(live)) {
            askTyped(live, new Tracked(viewer, totpEnabled, false, false, true));
            return;
        }
        open.put(viewer.uuid(), new Tracked(viewer, totpEnabled, false, false, false));
        markOpening(viewer.uuid());
        if (live != null) {
            menus.open(live, SPEC_ID, new PinSession(viewer, totpEnabled));
        }
    }

    /**
     * Ask a player who types for their entry. Only one prompt is out per player: asking while one is still open
     * would make the seam replace it, and the replaced anvil's close would arrive as the player dismissing it.
     */
    private void askTyped(Player player, Tracked tracked) {
        UUID id = tracked.viewer().uuid();
        Tracked previous = open.put(id, tracked);
        Long showing = typing.get(id);
        if (showing != null) {
            if (previous != null && stepOf(previous) == stepOf(tracked)) {
                return;
            }
            // The prompt on screen asks the wrong question (a verify prompt still up when a reset sends the player to
            // create a PIN). Dropping its token makes its answer count for nothing, and the new prompt replaces it.
            typing.remove(id, showing);
        }
        long token = promptTokens.incrementAndGet();
        typing.put(id, token);
        askedAt.put(id, System.nanoTime());
        typed.prompt(
                player,
                tracked.viewer(),
                stepOf(tracked),
                entered -> typedSubmitted(player, tracked.viewer(), token, entered),
                () -> typedClosed(id, token));
    }

    private void typedSubmitted(Player player, PlayerRef viewer, long token, String entered) {
        KeypadActions target = actions;
        if (!typing.remove(viewer.uuid(), token) || target == null) {
            return;
        }
        askedAt.remove(viewer.uuid());
        quickCloses.remove(viewer.uuid());
        // Only what the keypad could have entered counts as the keypad's digits: fullwidth digits become ASCII and
        // spaces go, and anything else reaches the policy or the verify as typed and is refused there.
        target.submit(player, viewer, SecureEntryInput.normalise(entered));
    }

    private static PinEntryPrompt.Step stepOf(Tracked tracked) {
        if (!tracked.creating()) {
            return PinEntryPrompt.Step.VERIFY;
        }
        return tracked.confirming() ? PinEntryPrompt.Step.CONFIRM : PinEntryPrompt.Step.CREATE;
    }

    /**
     * A typed prompt went away without an answer. Normally that is the player dismissing it and is settled like a closed
     * window. A prompt gone within {@link #QUICK_CLOSE} of being asked for, while the player is still held, may be one
     * the client could not show at all, and asking again at once would ask again every tick; so each such close in a
     * row waits twice as long before the next ask, up to {@link #MAX_BACKOFF}. The freeze's entry timeout still bounds
     * the whole wait, so a player who truly cannot answer is still let go when it runs out.
     */
    private void typedClosed(UUID viewer, long token) {
        if (!typing.remove(viewer, token)) {
            return;
        }
        Long asked = askedAt.remove(viewer);
        boolean quick = asked != null && System.nanoTime() - asked < QUICK_CLOSE.toNanos();
        Tracked tracked = open.get(viewer);
        if (!quick || tracked == null || suppressReopen.contains(viewer) || !frozen.test(viewer)) {
            quickCloses.remove(viewer);
            closed(viewer);
            return;
        }
        int streak = quickCloses.merge(viewer, 1, Integer::sum);
        Duration delay = backoff(streak);
        if (streak >= STREAK_WORTH_LOGGING && backoffLogged.compareAndSet(false, true)) {
            log.warn(
                    "event=security_pin_prompt_backoff player={} closes_in_a_row={} delay_ms={} hint=check the input"
                            + " mode of security.verify-pin and security.create-pin in text-input.conf",
                    tracked.viewer().name(),
                    streak,
                    delay.toMillis());
        }
        scheduler.laterEntity(tracked.viewer(), delay, () -> {
            if (frozen.test(viewer)) {
                reopen(viewer);
            }
        });
    }

    private static Duration backoff(int streak) {
        Duration delay = FIRST_BACKOFF.multipliedBy(1L << Math.min(streak - 1, 8));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private void markOpening(UUID viewer) {
        inFlight.merge(viewer, 1, Integer::sum);
    }

    private void closeLive(UUID viewer) {
        Player live = Bukkit.getPlayer(viewer);
        if (live != null && live.isOnline()) {
            live.closeInventory();
        }
    }

    /** The masked-entry display: one asterisk per entered digit, resolved from the catalog, never the digits. */
    private String entryDisplay(MenuContext ctx) {
        PinSession session = sessionOf(ctx);
        int length = session == null ? 0 : session.length();
        return messages.resolve(
                BukkitRefs.toRef(ctx.viewer()),
                SecurityMessageKey.SECURITY_VERIFY_KEYPAD_ENTRY,
                Map.of("entry", mask(length)));
    }

    /** One digit button's label, resolved from the catalog so it stays localized and operator-editable. */
    private String digitLabel(MenuContext ctx, int digit) {
        return messages.resolve(
                BukkitRefs.toRef(ctx.viewer()),
                SecurityMessageKey.SECURITY_VERIFY_KEYPAD_DIGIT,
                Map.of("digit", Integer.toString(digit)));
    }

    private void pressDigit(MenuActionContext ctx, int digit) {
        PinSession session = ctx.subject(PinSession.class);
        session.append(Character.forDigit(digit, 10), MAX_ENTRY);
        ctx.control().refresh();
        // The click is what tells the player the pad took the tap; the masked display only says how many it has.
        feedback.keyPress(BukkitRefs.toRef(ctx.viewer()));
    }

    private void pressClear(MenuActionContext ctx) {
        ctx.subject(PinSession.class).reset();
        ctx.control().refresh();
    }

    private void pressSubmit(MenuActionContext ctx, KeypadActions actions) {
        String entered = ctx.subject(PinSession.class).consume();
        ctx.control().refresh();
        actions.submit(ctx.player(), BukkitRefs.toRef(ctx.viewer()), entered);
    }

    private void pressTotp(MenuActionContext ctx, KeypadActions actions) {
        actions.requestTotp(ctx.player(), BukkitRefs.toRef(ctx.viewer()));
    }

    private boolean totpEnabled(MenuContext ctx) {
        PinSession session = sessionOf(ctx);
        return session != null && session.totpEnabled();
    }

    private static @Nullable PinSession sessionOf(MenuContext ctx) {
        return ctx.subjectRaw()
                .filter(PinSession.class::isInstance)
                .map(PinSession.class::cast)
                .orElse(null);
    }

    /** Render {@code length} asterisks so the display shows how many digits are entered without revealing them. */
    private static String mask(int length) {
        return "*".repeat(Math.max(0, length));
    }

    /**
     * The per-viewer tracking a close/reopen needs: who is at the window, whether their code button shows, which of
     * the two pads they are on (and, for a typed new PIN, which of its two entries), and whether they type rather than
     * tap, so what is shown again is what they were actually using.
     */
    private record Tracked(
            PlayerRef viewer, boolean totpEnabled, boolean creating, boolean confirming, boolean typed) {}

    /**
     * The per-viewer keypad state carried as the open menu's subject: who is verifying, whether they hold a TOTP
     * factor (so the code button shows), and the digits entered so far. The entry is a plaintext PIN or code in
     * flight; it lives only on this subject server-side, is never logged and never rendered (only its {@link #length}
     * is, as a mask), and is consumed the moment submit reads it. It is mutated and read only on the viewer's own
     * entity thread (the digit/clear/submit actions and the render both run there), so it needs no lock; the close
     * listener reads only the immutable {@link #viewer} and {@link #totpEnabled}, which are safe from any thread.
     */
    static final class PinSession {

        private final PlayerRef viewer;
        private final boolean totpEnabled;
        private final StringBuilder entry = new StringBuilder();

        PinSession(PlayerRef viewer, boolean totpEnabled) {
            this.viewer = Objects.requireNonNull(viewer, "viewer");
            this.totpEnabled = totpEnabled;
        }

        PlayerRef viewer() {
            return viewer;
        }

        boolean totpEnabled() {
            return totpEnabled;
        }

        int length() {
            return entry.length();
        }

        void append(char digit, int max) {
            if (entry.length() < max) {
                entry.append(digit);
            }
        }

        void reset() {
            entry.setLength(0);
        }

        String consume() {
            String entered = entry.toString();
            entry.setLength(0);
            return entered;
        }
    }
}
