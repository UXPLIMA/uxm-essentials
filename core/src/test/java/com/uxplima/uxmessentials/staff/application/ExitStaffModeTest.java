package com.uxplima.uxmessentials.staff.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeExited;
import org.junit.jupiter.api.Test;

class ExitStaffModeTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "Mod");

    private ExitStaffMode exitMode(StaffTestFakes fakes) {
        return new ExitStaffMode(
                fakes.store, fakes.repository, fakes.capture, fakes.vanish, fakes.notifier(), fakes.events);
    }

    @Test
    void loadsRestoresDeletesAndClearsInThatOrder() {
        StaffTestFakes fakes = new StaffTestFakes();
        SavedLoadout stored = StaffTestFakes.sampleLoadout();
        fakes.store.active.put(ACTOR.uuid(), "default");
        fakes.repository.rows.put(ACTOR.uuid(), stored);

        var result = exitMode(fakes).exit(ACTOR);

        assertThat(result.isOk()).isTrue();
        // Restore-then-delete: the real loadout is back before the durable copy is dropped.
        assertThat(fakes.calls)
                .containsExactly("load", "restore", "delete", "clear", "vanish:false", "publish:StaffModeExited");
        assertThat(fakes.capture.restored).containsExactly(stored);
        assertThat(fakes.repository.rows).doesNotContainKey(ACTOR.uuid());
        assertThat(fakes.store.isActive(ACTOR)).isFalse();
        assertThat(fakes.events.published).containsExactly(new StaffModeExited(ACTOR));
        assertThat(fakes.sentKeys).containsExactly(StaffMessageKey.STAFF_MODE_OFF);
    }

    @Test
    void notInStaffModeIsANoOp() {
        StaffTestFakes fakes = new StaffTestFakes();

        var result = exitMode(fakes).exit(ACTOR);

        assertThat(result.isErr()).isTrue();
        assertThat(fakes.calls).isEmpty();
        assertThat(fakes.events.published).isEmpty();
        assertThat(fakes.sentKeys).isEmpty();
    }
}
