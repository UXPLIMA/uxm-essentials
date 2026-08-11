package com.uxplima.uxmessentials.nametags.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.nametags.adapter.outbound.PacketNametagPresenter;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The published nametag write: the reconcile pass for one wearer. It goes through {@code update} rather than
 * {@code show}, which is what makes a refresh re-select the format instead of redrawing the old one.
 */
class NametagActionsTest {

    private ServerMock server;
    private PlayerMock alice;
    private PlayerRef who;
    private PacketNametagPresenter presenter;
    private ActionDoubles.InlineScheduler scheduler;
    private NametagActions actions;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        alice = server.addPlayer("Alice");
        who = new PlayerRef(alice.getUniqueId(), alice.getName());
        presenter = mock(PacketNametagPresenter.class);
        scheduler = new ActionDoubles.InlineScheduler();
        actions = new NametagActions(presenter, new QueryDoubles.MapLookup().with(who), scheduler);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void refreshReconcilesTheWearerOnTheirOwnThread() {
        assertThat(actions.refresh(who.uuid()).join().succeeded()).isTrue();

        verify(presenter).update(alice);
        assertThat(scheduler.entityCalls()).isOne();
        assertThat(scheduler.asyncCalls()).isZero();
    }

    @Test
    void aPlayerWhoLeftIsAnsweredOfflineRatherThanLeftHanging() {
        scheduler.retire(who);

        assertThat(actions.refresh(who.uuid()).join().failureOrThrow().code()).isEqualTo(UxmFailure.PLAYER_OFFLINE);
        verifyNoInteractions(presenter);
    }

    @Test
    void aPlayerNobodyKnowsIsAnsweredRatherThanRejected() {
        assertThat(actions.refresh(UUID.randomUUID()).join().failureOrThrow().code())
                .isEqualTo(UxmFailure.PLAYER_OFFLINE);
        verifyNoInteractions(presenter);
    }
}
