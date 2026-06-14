package com.uxplima.uxmessentials.staff.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutCapture;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutRepository;
import com.uxplima.uxmessentials.staff.application.port.StaffModeStore;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeExited;

/**
 * {@code /staffmode} (leaving): take a staff member out of staff mode and put their real loadout back.
 *
 * <p><b>Restore-then-delete invariant (item-loss safety).</b> The committed loadout row is the one true copy
 * of the player's real inventory while they are in staff mode, so it is loaded and restored onto the player
 * <i>before</i> the row is deleted — the database row is dropped only once the items are safely back on the
 * player. The sequence is:
 *
 * <ol>
 *   <li>{@code loadoutRepository.load(uuid)} → {@code capture.restore(actor, loadout)} — put the real
 *       loadout back (when a row exists);
 *   <li>{@code loadoutRepository.delete(uuid)} — now drop the stored copy;
 *   <li>{@code store.clear(actor)} — clear the in-memory staff-mode marker;
 *   <li>{@code vanish.setVanished(actor, false)} — reveal the player again through the presence seam;
 *   <li>{@code events.publish(StaffModeExited)} — announce the restored state.
 * </ol>
 *
 * <p>Leaving while not in staff mode is a no-op: nothing to restore, nothing to delete.
 */
public final class ExitStaffMode {

    private final StaffModeStore store;
    private final StaffLoadoutRepository loadoutRepository;
    private final StaffLoadoutCapture capture;
    private final StaffVanish vanish;
    private final StaffNotifier notifier;
    private final DomainEventPublisher events;

    public ExitStaffMode(
            StaffModeStore store,
            StaffLoadoutRepository loadoutRepository,
            StaffLoadoutCapture capture,
            StaffVanish vanish,
            StaffNotifier notifier,
            DomainEventPublisher events) {
        this.store = Objects.requireNonNull(store, "store");
        this.loadoutRepository = Objects.requireNonNull(loadoutRepository, "loadoutRepository");
        this.capture = Objects.requireNonNull(capture, "capture");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Take {@code actor} out of staff mode and restore their real loadout. */
    public Result<Unit, Unit> exit(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        if (!store.isActive(actor)) {
            // Not in staff mode: nothing to restore, nothing to delete.
            return Result.err(Unit.INSTANCE);
        }
        // RESTORE FIRST: put the real loadout back before deleting the durable copy.
        loadoutRepository.load(actor.uuid()).ifPresent(loadout -> capture.restore(actor, loadout));
        loadoutRepository.delete(actor.uuid());
        store.clear(actor);
        vanish.setVanished(actor, false);
        events.publish(new StaffModeExited(actor));
        notifier.send(actor, StaffMessageKey.STAFF_MODE_OFF);
        return Result.ok();
    }
}
