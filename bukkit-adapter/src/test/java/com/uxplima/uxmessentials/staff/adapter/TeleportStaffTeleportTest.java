package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.staff.adapter.outbound.TeleportStaffTeleport;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * {@link TeleportStaffTeleport} resolves the target's live location and launches an admin teleport: an offline
 * target is reported as unable-to-start without touching the engine, and an online target's launch result is
 * passed straight through.
 */
class TeleportStaffTeleportTest {

    private static final PlayerRef STAFF = new PlayerRef(UUID.randomUUID(), "Staff");

    private ServerMock server;
    private TeleportEngine engine;
    private TeleportStaffTeleport teleport;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        engine = mock(TeleportEngine.class);
        teleport = new TeleportStaffTeleport(engine, server);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void offlineTargetCannotBeStarted() {
        PlayerRef offline = new PlayerRef(UUID.randomUUID(), "Ghost");

        assertThat(teleport.teleportTo(STAFF, offline)).isFalse();
    }

    @Test
    void onlineTargetLaunchesAnAdminTeleport() {
        Player target = server.addPlayer("Target");
        PlayerRef targetRef = new PlayerRef(target.getUniqueId(), target.getName());
        when(engine.launch(eq(STAFF), any(Destination.class), eq(TeleportKind.ADMIN)))
                .thenReturn(Result.ok());

        assertThat(teleport.teleportTo(STAFF, targetRef)).isTrue();
    }

    @Test
    void aFailedLaunchIsReportedAsNotStarted() {
        Player target = server.addPlayer("Target");
        PlayerRef targetRef = new PlayerRef(target.getUniqueId(), target.getName());
        Result<Unit, TeleportError> failed = Result.err(TeleportError.JAILED);
        when(engine.launch(eq(STAFF), any(Destination.class), eq(TeleportKind.ADMIN)))
                .thenReturn(failed);

        assertThat(teleport.teleportTo(STAFF, targetRef)).isFalse();
    }
}
