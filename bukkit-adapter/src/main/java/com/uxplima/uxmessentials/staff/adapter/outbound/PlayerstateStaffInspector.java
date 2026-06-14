package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.OpenContainer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffInspector;
import org.jspecify.annotations.NullMarked;

/**
 * The playerstate-backed {@link StaffInspector}: the EXAMINE gadget opens the target's inventory through the
 * existing playerstate module's {@link OpenContainer#openInventory} use case, so staff mode reuses the
 * {@code /invsee} machinery rather than owning a second inventory-view path.
 *
 * <p>This impl is bound only when the playerstate module is enabled; with playerstate off the wiring binds
 * {@link StaffInspector#NONE}, so the EXAMINE gadget degrades to just the info line (the listener still shows
 * {@code STAFF_EXAMINE_INFO} regardless) rather than failing.
 */
@NullMarked
public final class PlayerstateStaffInspector implements StaffInspector {

    private final OpenContainer openContainer;

    public PlayerstateStaffInspector(OpenContainer openContainer) {
        this.openContainer = Objects.requireNonNull(openContainer, "openContainer");
    }

    @Override
    public void inspect(PlayerRef looker, PlayerRef target) {
        Objects.requireNonNull(looker, "looker");
        Objects.requireNonNull(target, "target");
        openContainer.openInventory(looker, target);
    }
}
