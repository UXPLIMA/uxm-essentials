package com.uxplima.uxmessentials.staff.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffModeStoreImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The published staff-mode read, over the very map the gadgets consult. */
class StaffQueriesTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    private StaffModeStoreImpl store;
    private StaffQueries queries;

    @BeforeEach
    void setUp() {
        store = new StaffModeStoreImpl();
        queries = new StaffQueries(store);
    }

    @Test
    void somebodyOffDutyIsNotInStaffModeAndNamesNoMode() {
        assertThat(queries.isInStaffMode(ALICE.uuid())).isFalse();
        assertThat(queries.modeOf(ALICE.uuid())).isEmpty();
        assertThat(queries.inStaffMode()).isEmpty();
    }

    @Test
    void goingOnDutyNamesTheModeTheyAreOnRatherThanOnlyTheFlag() {
        store.setActive(ALICE, "admin");

        assertThat(queries.isInStaffMode(ALICE.uuid())).isTrue();
        assertThat(queries.modeOf(ALICE.uuid())).contains("admin");
        assertThat(queries.inStaffMode()).containsExactly(ALICE.uuid());
    }

    @Test
    void twoStaffOnTwoModesAreBothListedAndComingOffDutyRemovesOne() {
        store.setActive(ALICE, "admin");
        store.setActive(BOB, "helper");

        assertThat(queries.inStaffMode()).containsExactlyInAnyOrder(ALICE.uuid(), BOB.uuid());
        assertThat(queries.modeOf(BOB.uuid())).contains("helper");

        store.clear(ALICE);

        assertThat(queries.inStaffMode()).containsExactly(BOB.uuid());
        assertThat(queries.modeOf(ALICE.uuid())).isEmpty();
    }
}
