package com.uxplima.uxmessentials.kits.adapter.inbound.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class KitsJoinListenerTest {

    @Test
    void onJoin_withExistingPlayer_doesNotGrantAnyKits() {
        // Arrange
        Player player = mock(Player.class);
        when(player.hasPlayedBefore()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alice");

        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);

        KitRepository repository = mock(KitRepository.class);
        KitGranter granter = mock(KitGranter.class);
        KitAccess access = mock(KitAccess.class);

        KitsJoinListener listener = new KitsJoinListener(repository, granter, access);

        // Act
        listener.onJoin(event);

        // Assert
        verify(repository, never()).all();
    }

    @Test
    void onJoin_withNewPlayer_grantsOnlyFirstJoinKits() {
        // Arrange
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.hasPlayedBefore()).thenReturn(false);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alice");

        PlayerRef who = new PlayerRef(uuid, "Alice");

        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);

        KitDefinition firstJoinKit = KitDefinition.builder()
                .id(KitId.of("first-join-kit"))
                .firstJoin(true)
                .build();
        KitDefinition regularKit =
                KitDefinition.builder().id(KitId.of("regular-kit")).build();

        KitRepository repository = mock(KitRepository.class);
        when(repository.all()).thenReturn(List.of(firstJoinKit, regularKit));

        KitGranter granter = mock(KitGranter.class);
        KitAccess access = mock(KitAccess.class);
        when(access.resolveVariant(who, firstJoinKit)).thenReturn(firstJoinKit);

        KitsJoinListener listener = new KitsJoinListener(repository, granter, access);

        // Act
        listener.onJoin(event);

        // Assert
        verify(granter).grant(who, firstJoinKit);
        verify(granter, never()).grant(who, regularKit);
        verify(access, never()).recordClaim(who, firstJoinKit);
    }

    @Test
    void onJoin_grantsOneTimeFirstJoinKit_recordsClaim() {
        // Arrange
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.hasPlayedBefore()).thenReturn(false);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alice");

        PlayerRef who = new PlayerRef(uuid, "Alice");

        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);

        KitDefinition oneTimeFirstJoinKit = KitDefinition.builder()
                .id(KitId.of("one-time-first-join"))
                .oneTime(true)
                .firstJoin(true)
                .build();

        KitRepository repository = mock(KitRepository.class);
        when(repository.all()).thenReturn(List.of(oneTimeFirstJoinKit));

        KitGranter granter = mock(KitGranter.class);
        KitAccess access = mock(KitAccess.class);
        when(access.resolveVariant(who, oneTimeFirstJoinKit)).thenReturn(oneTimeFirstJoinKit);

        KitsJoinListener listener = new KitsJoinListener(repository, granter, access);

        // Act
        listener.onJoin(event);

        // Assert
        verify(granter).grant(who, oneTimeFirstJoinKit);
        verify(access).recordClaim(who, oneTimeFirstJoinKit);
    }
}
