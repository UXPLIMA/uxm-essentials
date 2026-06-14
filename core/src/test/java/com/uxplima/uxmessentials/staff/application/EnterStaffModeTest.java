package com.uxplima.uxmessentials.staff.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeEntered;
import org.junit.jupiter.api.Test;

class EnterStaffModeTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "Mod");

    private EnterStaffMode enterMode(StaffTestFakes fakes, boolean vanishOnEnter) {
        return new EnterStaffMode(
                fakes.store,
                fakes.repository,
                fakes.capture,
                fakes.vanish,
                fakes.notifier(),
                fakes.events,
                "default",
                vanishOnEnter);
    }

    @Test
    void persistsTheLoadoutBeforeSettingActiveAndBeforeApplyingTheGadgetHotbar() {
        StaffTestFakes fakes = new StaffTestFakes();

        var result = enterMode(fakes, true).enter(ACTOR);

        assertThat(result.isOk()).isTrue();
        // The commit-before-swap invariant: capture → save (durable) → set-active → publish → apply-hotbar.
        assertThat(fakes.calls)
                .containsExactly(
                        "capture", "save", "setActive", "publish:StaffModeEntered", "applyGadgetHotbar", "vanish:true");
        // The loadout is the captured one, and it was saved before the hotbar swap.
        assertThat(fakes.repository.rows.get(ACTOR.uuid())).isEqualTo(fakes.capture.toCapture);
        assertThat(fakes.calls.indexOf("save")).isLessThan(fakes.calls.indexOf("applyGadgetHotbar"));
    }

    @Test
    void publishesTheEnteredEventAndNotifiesTheActor() {
        StaffTestFakes fakes = new StaffTestFakes();

        enterMode(fakes, false).enter(ACTOR);

        assertThat(fakes.events.published).containsExactly(new StaffModeEntered(ACTOR));
        assertThat(fakes.sentKeys).containsExactly(StaffMessageKey.STAFF_MODE_ON);
        // vanish-on-enter false still calls the seam, with false.
        assertThat(fakes.vanish.states).containsExactly(false);
    }

    @Test
    void alreadyInStaffModeIsANoOpWithNoSecondCaptureOrSave() {
        StaffTestFakes fakes = new StaffTestFakes();
        fakes.store.active.put(ACTOR.uuid(), "default");

        var result = enterMode(fakes, true).enter(ACTOR);

        assertThat(result.isErr()).isTrue();
        // No capture, no save, no swap — the committed loadout is the one true copy.
        assertThat(fakes.calls).isEmpty();
        assertThat(fakes.repository.rows).doesNotContainKey(ACTOR.uuid());
        assertThat(fakes.sentKeys).containsExactly(StaffMessageKey.STAFF_MODE_ALREADY);
    }
}
