package com.uxplima.uxmessentials.security.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.security.adapter.inbound.gui.PinKeypadLayout.Button;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The join-verification keypad as an editable-but-locked chest window: the {@link PinKeypadLayout} phone-pad the frozen
 * player taps their PIN or authenticator code into. This view owns the window lifecycle — building it against a
 * {@link PinKeypadHolder}, opening and reopening it on the player's region thread, updating the masked entry after each
 * keystroke, and closing it on a successful verification — while the click routing and the verification decision live
 * in {@link PinKeypadListener} and the controller. It holds no verdict logic; it renders and tracks.
 *
 * <p>A bespoke inventory leaf (on the {@code createInventory} allow-list) rather than a spec menu, because it captures
 * live per-keystroke clicks and force-reopens on an escape — behaviour the declarative engine deliberately does not
 * model. Every live-player touch hops to that player's region thread through the injected {@link Scheduler}, so it is
 * Folia-safe. The entered digits live only in the holder and are never logged.
 */
@NullMarked
public final class PinKeypadView {

    /** The most digits the pad accepts — comfortably above an 8-digit PIN and a 6-digit code. */
    private static final int MAX_ENTRY = 12;

    private final Messages messages;
    private final Scheduler scheduler;
    private final PinKeypadLayout layout = new PinKeypadLayout();

    /** viewer UUID → their currently-open keypad holder; the tracking that lets stop() close every window. */
    private final Map<UUID, PinKeypadHolder> open = new ConcurrentHashMap<>();

    /** Viewers whose next keypad close is a deliberate handoff (to the TOTP prompt), so it must not reopen. */
    private final Set<UUID> suppressReopen = ConcurrentHashMap.newKeySet();

    public PinKeypadView(Messages messages, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Open a fresh keypad for {@code viewer} on their region thread; {@code totpEnabled} shows the "type a code" button. */
    public void open(Player player, PlayerRef viewer, boolean totpEnabled) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> openResolved(viewer, totpEnabled));
    }

    /** Reopen the keypad for a still-frozen viewer who escaped their window; a fresh holder with an empty entry. */
    void reopen(PinKeypadHolder previous) {
        scheduler.onEntity(previous.viewer(), () -> openResolved(previous.viewer(), previous.totpEnabled()));
    }

    /** Close the viewer's keypad and forget it — the successful-verification path. */
    public void closeFor(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        open.remove(viewer.uuid());
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                live.closeInventory();
            }
        });
    }

    /** Close every open keypad and drop all tracking — module stop, so a disable leaves no locked window. */
    public void closeAll() {
        for (PinKeypadHolder holder : Set.copyOf(open.values())) {
            closeFor(holder.viewer());
        }
        open.clear();
        suppressReopen.clear();
    }

    /** Mark the viewer's next keypad close as a deliberate handoff, so {@link PinKeypadListener} will not reopen it. */
    public void suppressNextClose(PlayerRef viewer) {
        suppressReopen.add(Objects.requireNonNull(viewer, "viewer").uuid());
    }

    /** Consume the suppress flag for {@code viewer}: true (and cleared) when the close was a deliberate handoff. */
    boolean consumeSuppress(UUID viewer) {
        return suppressReopen.remove(viewer);
    }

    /** Decode a raw slot into the button it carries. */
    Button buttonAt(int rawSlot) {
        return layout.buttonAt(rawSlot);
    }

    /** Append a digit to the holder's entry and repaint the masked display. */
    void appendDigit(PinKeypadHolder holder, int digit) {
        holder.append(Character.forDigit(digit, 10), MAX_ENTRY);
        renderEntry(holder);
    }

    /** Empty the holder's entry and repaint the masked display — the clear button. */
    void clearEntry(PinKeypadHolder holder) {
        holder.reset();
        renderEntry(holder);
    }

    /** Read and clear the entered digits (repainting the emptied display) — the submit button's consume. */
    String consume(PinKeypadHolder holder) {
        String entered = holder.entry();
        holder.reset();
        renderEntry(holder);
        return entered;
    }

    private void openResolved(PlayerRef viewer, boolean totpEnabled) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        PinKeypadHolder holder = new PinKeypadHolder(viewer, totpEnabled);
        Inventory inv = Bukkit.createInventory(holder, PinKeypadLayout.SIZE, title(viewer));
        holder.attach(inv);
        layout.render(inv, messages, viewer, 0, totpEnabled);
        open.put(viewer.uuid(), holder);
        live.openInventory(inv);
    }

    private void renderEntry(PinKeypadHolder holder) {
        layout.renderEntry(holder.getInventory(), messages, holder.viewer(), holder.length());
    }

    private Component title(PlayerRef viewer) {
        return StyledText.render(messages.resolve(viewer, SecurityMessageKey.SECURITY_VERIFY_KEYPAD_TITLE, Map.of()));
    }
}
