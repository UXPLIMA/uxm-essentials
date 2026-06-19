package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.worlds.domain.WorldTeleportCause;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BukkitWorldTeleporterTest {

    private final TeleportEngine engine = mock(TeleportEngine.class);
    private final BukkitWorldTeleporter teleporter = new BukkitWorldTeleporter(engine);
    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Steve");
    private final Position to = new Position(new WorldRef(UUID.randomUUID(), "creative"), 10.5, 64, -3.5, 90f, 0f);

    @Test
    void spawnCauseLaunchesWithSpawnKindAtTheGivenPosition() {
        when(engine.launch(any(), any(), any())).thenReturn(Result.ok());

        boolean accepted = teleporter.teleport(who, to, WorldTeleportCause.SPAWN);

        assertThat(accepted).isTrue();
        ArgumentCaptor<Destination> destination = ArgumentCaptor.forClass(Destination.class);
        ArgumentCaptor<TeleportKind> kind = ArgumentCaptor.forClass(TeleportKind.class);
        verify(engine).launch(eq(who), destination.capture(), kind.capture());
        assertThat(kind.getValue()).isEqualTo(TeleportKind.SPAWN);
        assertThat(destination.getValue().position()).isEqualTo(to);
    }

    @Test
    void adminCauseLaunchesWithAdminKind() {
        when(engine.launch(any(), any(), any())).thenReturn(Result.ok());

        teleporter.teleport(who, to, WorldTeleportCause.ADMIN);

        ArgumentCaptor<TeleportKind> kind = ArgumentCaptor.forClass(TeleportKind.class);
        verify(engine).launch(any(), any(), kind.capture());
        assertThat(kind.getValue()).isEqualTo(TeleportKind.ADMIN);
    }

    @Test
    void returnsTrueWhenTheEngineAcceptsTheLaunch() {
        when(engine.launch(any(), any(), any())).thenReturn(Result.ok());

        assertThat(teleporter.teleport(who, to, WorldTeleportCause.SPAWN)).isTrue();
    }

    @Test
    void returnsFalseWhenTheEngineRejectsTheLaunch() {
        when(engine.launch(any(), any(), any())).thenReturn(Result.err(TeleportError.ON_COOLDOWN));

        assertThat(teleporter.teleport(who, to, WorldTeleportCause.SPAWN)).isFalse();
    }
}
