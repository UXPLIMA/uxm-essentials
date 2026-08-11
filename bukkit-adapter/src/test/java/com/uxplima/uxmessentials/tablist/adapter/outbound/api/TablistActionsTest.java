package com.uxplima.uxmessentials.tablist.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import com.uxplima.uxmessentials.tablist.adapter.outbound.TablistRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The published tab-list write: the timer's own repaint for one viewer, on that viewer's thread. */
class TablistActionsTest {

    private ServerMock server;
    private PlayerMock alice;
    private PlayerRef who;
    private TablistRenderer renderer;
    private ActionDoubles.InlineScheduler scheduler;
    private TablistActions actions;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        alice = server.addPlayer("Alice");
        who = new PlayerRef(alice.getUniqueId(), alice.getName());
        renderer = mock(TablistRenderer.class);
        scheduler = new ActionDoubles.InlineScheduler();
        actions = new TablistActions(renderer, new QueryDoubles.MapLookup().with(who), scheduler);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void refreshRepaintsTheViewerOnTheirOwnThread() {
        assertThat(actions.refresh(who.uuid()).join().succeeded()).isTrue();

        verify(renderer).renderFor(alice);
        assertThat(scheduler.entityCalls()).isOne();
        assertThat(scheduler.asyncCalls()).isZero();
    }

    @Test
    void aPlayerWhoLeftIsAnsweredOfflineRatherThanLeftHanging() {
        scheduler.retire(who);

        assertThat(actions.refresh(who.uuid()).join().failureOrThrow().code()).isEqualTo(UxmFailure.PLAYER_OFFLINE);
        verifyNoInteractions(renderer);
    }

    @Test
    void aPlayerNobodyKnowsIsAnsweredRatherThanRejected() {
        assertThat(actions.refresh(UUID.randomUUID()).join().failureOrThrow().code())
                .isEqualTo(UxmFailure.PLAYER_OFFLINE);
        verifyNoInteractions(renderer);
    }
}
