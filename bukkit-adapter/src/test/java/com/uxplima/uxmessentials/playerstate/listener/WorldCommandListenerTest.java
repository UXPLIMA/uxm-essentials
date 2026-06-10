package com.uxplima.uxmessentials.playerstate.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.uxplima.uxmessentials.playerstate.adapter.inbound.listener.WorldCommandListener;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.playerstate.application.WorldCommandPolicy;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the per-world command blocker listener: a blocked command in the player's world is cancelled and
 * the player is told the command is disabled, a non-blocked command (and a command run by a bypass holder)
 * passes through, and the plugin-namespaced {@code /uxmessentials:<cmd>} form is caught the same as the bare
 * label. The {@link Messages} and {@link MessageSink} ports are mocked so the cancel decision and the
 * delivered key are observable without a live server.
 */
class WorldCommandListenerTest {

    private Messages messages;
    private MessageSink sink;
    private Player player;

    @BeforeEach
    void setUp() {
        messages = mock(Messages.class);
        sink = mock(MessageSink.class);
        when(messages.resolve(any(), any(), any())).thenReturn("blocked");

        World creative = mock(World.class);
        when(creative.getName()).thenReturn("creative");

        player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Builder");
        when(player.getWorld()).thenReturn(creative);
        when(player.hasPermission(WorldCommandListener.BYPASS_NODE)).thenReturn(false);
    }

    private WorldCommandListener listener(Map<String, List<String>> blocks) {
        return new WorldCommandListener(new WorldCommandPolicy(blocks), messages, sink);
    }

    @Test
    void cancelsABlockedCommandAndNotifiesThePlayer() {
        WorldCommandListener listener = listener(Map.of("creative", List.of("tpa", "warp")));

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/tpa Steve", null);
        listener.onCommand(event);

        assertThat(event.isCancelled()).isTrue();
        verify(messages).resolve(any(PlayerRef.class), eq(PlayerstateMessageKey.WORLD_COMMAND_BLOCKED), any());
        verify(sink).deliver(any(PlayerRef.class), eq("blocked"));
    }

    @Test
    void catchesTheNamespacedForm() {
        WorldCommandListener listener = listener(Map.of("creative", List.of("tpa")));

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/uxmessentials:tpa", null);
        listener.onCommand(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void leavesANonBlockedCommandAlone() {
        WorldCommandListener listener = listener(Map.of("creative", List.of("tpa")));

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/home", null);
        listener.onCommand(event);

        assertThat(event.isCancelled()).isFalse();
        verify(sink, never()).deliver(any(), anyString());
    }

    @Test
    void bypassHolderIsNeverBlocked() {
        when(player.hasPermission(WorldCommandListener.BYPASS_NODE)).thenReturn(true);
        WorldCommandListener listener = listener(Map.of("creative", List.of("tpa")));

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/tpa Steve", null);
        listener.onCommand(event);

        assertThat(event.isCancelled()).isFalse();
        verify(sink, never()).deliver(any(), anyString());
    }

    @Test
    void anEmptyMapShortCircuitsWithoutReadingTheWorld() {
        WorldCommandListener listener = listener(Map.of());

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/tpa Steve", null);
        listener.onCommand(event);

        assertThat(event.isCancelled()).isFalse();
        verify(player, never()).getWorld();
    }
}
