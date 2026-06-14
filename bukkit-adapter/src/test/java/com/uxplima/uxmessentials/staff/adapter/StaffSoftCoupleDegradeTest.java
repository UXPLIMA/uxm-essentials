package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import com.uxplima.uxmessentials.staff.application.port.StaffInspector;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import org.junit.jupiter.api.Test;

/**
 * The soft couples degrade when their source module is off: the rebindable holders start on each port's
 * {@code NONE} and never throw while unbound (presence/playerstate/messaging disabled), and bind through to the
 * real impl once their source module is enabled. Mirrors the messaging {@code MutableMutePolicy} contract.
 */
class StaffSoftCoupleDegradeTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "Bob");

    @Test
    void anUnboundVanishHolderIsASilentNoOp() {
        MutableStaffVanish holder = new MutableStaffVanish();
        assertThatCode(() -> holder.setVanished(WHO, true)).doesNotThrowAnyException();
    }

    @Test
    void aBoundVanishHolderForwardsToTheRealImpl() {
        MutableStaffVanish holder = new MutableStaffVanish();
        List<Boolean> seen = new ArrayList<>();
        holder.bind((who, vanished) -> seen.add(vanished));

        holder.setVanished(WHO, true);

        assertThat(seen).containsExactly(true);
    }

    @Test
    void anUnboundInspectorHolderIsASilentNoOp() {
        MutableStaffInspector holder = new MutableStaffInspector();
        assertThatCode(() -> holder.inspect(WHO, TARGET)).doesNotThrowAnyException();
    }

    @Test
    void aBoundInspectorHolderForwardsToTheRealImpl() {
        MutableStaffInspector holder = new MutableStaffInspector();
        List<PlayerRef> inspected = new ArrayList<>();
        holder.bind((looker, target) -> inspected.add(target));

        holder.inspect(WHO, TARGET);

        assertThat(inspected).containsExactly(TARGET);
    }

    @Test
    void anUnboundChannelHolderHasAnEmptyAudienceAndASilentSend() {
        MutableStaffChannel holder = new MutableStaffChannel();
        assertThat(holder.onlineStaff()).isEmpty();
        assertThatCode(() -> holder.send(WHO, List.of(TARGET), "hi")).doesNotThrowAnyException();
    }

    @Test
    void thePortNoneDefaultsNeverThrow() {
        assertThatCode(() -> StaffVanish.NONE.setVanished(WHO, true)).doesNotThrowAnyException();
        assertThatCode(() -> StaffInspector.NONE.inspect(WHO, TARGET)).doesNotThrowAnyException();
        assertThat(StaffChannel.NONE.onlineStaff()).isEmpty();
        assertThatCode(() -> StaffChannel.NONE.send(WHO, List.of(TARGET), "hi")).doesNotThrowAnyException();
    }
}
