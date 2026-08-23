package com.uxplima.uxmessentials.security.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.security.adapter.VerificationFeedback;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
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
 * ever rendered); and while a player is still frozen the {@link PinKeypadCloseListener} reopens the window on any
 * escape, so verification cannot be skipped. A deliberate close (a successful verify, a lockout kick, the TOTP
 * handoff, or a module stop) is flagged through {@link #suppressNextClose} / {@link #closeFor} so it is not fought.
 * Every live-player touch runs on the viewer's own region thread through the engine and the injected {@link Scheduler},
 * so it stays Folia-safe.
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

    private final Menus menus;
    private final Messages messages;
    private final VerificationFeedback feedback;
    private final Scheduler scheduler;

    /** viewer UUID -> their open keypad's viewer + totp flag; the tracking that lets closeAll close and reopen rebuild. */
    private final Map<UUID, Tracked> open = new ConcurrentHashMap<>();

    /** Viewers whose next keypad close is a deliberate handoff or teardown, so it must not reopen. */
    private final Set<UUID> suppressReopen = ConcurrentHashMap.newKeySet();

    /** Viewers whose reopen is in flight, so the transient close that reopen fires cannot recurse into another reopen. */
    private final Set<UUID> reopening = ConcurrentHashMap.newKeySet();

    public PinKeypadView(Menus menus, Messages messages, VerificationFeedback feedback, Scheduler scheduler) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Register the keypad's per-button actions, its masked-display / digit-label placeholders, and the spec itself. */
    public void register(MenuBindings bindings, KeypadActions actions, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
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
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, log));
        menus.registerSpec(CREATE_SPEC_ID, MenuSpecs.loadOrBundled(CREATE_SPEC_RESOURCE, dataFolder, ROWS, log));
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
        open.put(viewer.uuid(), new Tracked(viewer, false, true));
        menus.open(viewer, CREATE_SPEC_ID, new PinSession(viewer, false));
    }

    /** Close the viewer's keypad, flagging the close so it is not reopened; the deliberate-close path (the close
     * listener drops the tracking once the flagged close lands). */
    public void closeFor(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        UUID id = viewer.uuid();
        suppressReopen.add(id);
        scheduler.onEntity(viewer, () -> closeLive(id));
    }

    /** Close every open keypad and drop all tracking, so a module disable leaves no locked window. */
    public void closeAll() {
        for (Tracked tracked : List.copyOf(open.values())) {
            closeFor(tracked.viewer());
        }
        open.clear();
        suppressReopen.clear();
        reopening.clear();
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
     * the viewer's tracked totp flag. Guarded against re-entrancy so the transient close the reopen's own
     * {@code openInventory} may fire cannot recurse into a second reopen.
     */
    void reopen(UUID viewer) {
        if (!reopening.add(viewer)) {
            return;
        }
        try {
            Tracked tracked = open.get(viewer);
            if (tracked == null) {
                return;
            }
            Player live = Bukkit.getPlayer(viewer);
            if (tracked.creating() && live != null) {
                openCreate(live, tracked.viewer());
                return;
            }
            show(tracked.viewer(), tracked.totpEnabled());
        } finally {
            reopening.remove(viewer);
        }
    }

    /** Drop the tracking for a keypad that closed without needing a reopen (a deliberate close, a re-auth escape, a quit). */
    void forget(UUID viewer) {
        open.remove(viewer);
    }

    private void show(PlayerRef viewer, boolean totpEnabled) {
        open.put(viewer.uuid(), new Tracked(viewer, totpEnabled, false));
        menus.open(viewer, SPEC_ID, new PinSession(viewer, totpEnabled));
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
                ctx.viewer(), SecurityMessageKey.SECURITY_VERIFY_KEYPAD_ENTRY, Map.of("entry", mask(length)));
    }

    /** One digit button's label, resolved from the catalog so it stays localized and operator-editable. */
    private String digitLabel(MenuContext ctx, int digit) {
        return messages.resolve(
                ctx.viewer(),
                SecurityMessageKey.SECURITY_VERIFY_KEYPAD_DIGIT,
                Map.of("digit", Integer.toString(digit)));
    }

    private void pressDigit(MenuActionContext ctx, int digit) {
        PinSession session = ctx.subject(PinSession.class);
        session.append(Character.forDigit(digit, 10), MAX_ENTRY);
        ctx.control().refresh();
        // The click is what tells the player the pad took the tap; the masked display only says how many it has.
        feedback.keyPress(ctx.viewer());
    }

    private void pressClear(MenuActionContext ctx) {
        ctx.subject(PinSession.class).reset();
        ctx.control().refresh();
    }

    private void pressSubmit(MenuActionContext ctx, KeypadActions actions) {
        String entered = ctx.subject(PinSession.class).consume();
        ctx.control().refresh();
        actions.submit(ctx.player(), ctx.viewer(), entered);
    }

    private void pressTotp(MenuActionContext ctx, KeypadActions actions) {
        actions.requestTotp(ctx.player(), ctx.viewer());
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
     * The per-viewer tracking a close/reopen needs: who is at the window, whether their code button shows, and which
     * of the two pads they are on, so a reopened window is the one they were actually using.
     */
    private record Tracked(PlayerRef viewer, boolean totpEnabled, boolean creating) {}

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
