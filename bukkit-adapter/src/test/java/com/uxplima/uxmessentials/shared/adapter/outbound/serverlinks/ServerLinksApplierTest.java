package com.uxplima.uxmessentials.shared.adapter.outbound.serverlinks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.ServerLinks;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/**
 * Coverage of {@link ServerLinksApplier}: a configured list clears the existing links and sets the parsed entries
 * (built-in type and custom label) on the global thread, a malformed entry is skipped while the valid ones still
 * apply, and an empty/all-malformed list leaves the live {@link ServerLinks} untouched (feature off).
 */
class ServerLinksApplierTest {

    @Test
    void clearsExistingThenSetsTypedAndLabelledLinks() {
        ServerLinks links = mock(ServerLinks.class);
        ServerLinks.ServerLink existing = mock(ServerLinks.ServerLink.class);
        when(links.getLinks()).thenReturn(List.of(existing));
        Server server = serverWith(links);

        applier(server)
                .apply(List.of(
                        new ServerLinksConfig.RawLink("WEBSITE", null, "https://example.com"),
                        new ServerLinksConfig.RawLink(null, "Discord", "https://discord.gg/x")));

        verify(links).removeLink(existing);
        verify(links).addLink(ServerLinks.Type.WEBSITE, URI.create("https://example.com"));
        verify(links)
                .addLink(Component.text("Discord for " + PlayerRef.SYSTEM_UUID), URI.create("https://discord.gg/x"));
    }

    @Test
    void skipsMalformedEntryButAppliesValidOnes() {
        ServerLinks links = mock(ServerLinks.class);
        when(links.getLinks()).thenReturn(List.of());
        Server server = serverWith(links);

        applier(server)
                .apply(List.of(
                        new ServerLinksConfig.RawLink("NONSENSE", null, "https://bad.example.com"),
                        new ServerLinksConfig.RawLink("STATUS", null, "https://status.example.com")));

        verify(links).addLink(ServerLinks.Type.STATUS, URI.create("https://status.example.com"));
        verify(links, never()).addLink(eq(ServerLinks.Type.WEBSITE), any());
    }

    /**
     * A label is read for each player: a catalogue line in their own language. The client names a built-in type in its
     * own language by itself, but a label is the operator's words, and one copy for the whole server read in one
     * language to every player.
     */
    @Test
    void aLabelReachesEachPlayerInTheirOwnWords() {
        ServerLinks global = mock(ServerLinks.class);
        ServerLinks own = mock(ServerLinks.class);
        when(global.copy()).thenReturn(own);
        when(own.getLinks()).thenReturn(List.of());
        Server server = serverWith(global);
        Player player = player();
        ServerLinksApplier applier = applier(server);
        applier.apply(List.of(new ServerLinksConfig.RawLink(null, "@custom.links.store", "https://store.example.com")));

        applier.sendTo(player);

        verify(own)
                .addLink(
                        Component.text("@custom.links.store for " + player.getUniqueId()),
                        URI.create("https://store.example.com"));
        verify(player).sendLinks(own);
    }

    @Test
    void typedLinksAloneSendNothingMoreToAPlayer() {
        ServerLinks global = mock(ServerLinks.class);
        when(global.getLinks()).thenReturn(List.of());
        Server server = serverWith(global);
        Player player = player();
        ServerLinksApplier applier = applier(server);
        applier.apply(List.of(new ServerLinksConfig.RawLink("WEBSITE", null, "https://example.com")));

        applier.sendTo(player);

        verify(player, never()).sendLinks(any());
    }

    @Test
    void aReloadSendsTheNewLinksToEveryPlayerOnline() {
        ServerLinks global = mock(ServerLinks.class);
        ServerLinks own = mock(ServerLinks.class);
        when(global.getLinks()).thenReturn(List.of());
        when(global.copy()).thenReturn(own);
        when(own.getLinks()).thenReturn(List.of());
        Server server = serverWith(global);
        Player player = player();
        doReturn(List.of(player)).when(server).getOnlinePlayers();

        applier(server).reload(List.of(new ServerLinksConfig.RawLink(null, "Store", "https://store.example.com")));

        verify(global)
                .addLink(Component.text("Store for " + PlayerRef.SYSTEM_UUID), URI.create("https://store.example.com"));
        verify(player).sendLinks(own);
    }

    /** A list emptied by a reload takes this plugin's links away; it used to keep them until a restart. */
    @Test
    void aReloadThatEmptiesTheListTakesTheLinksAway() {
        ServerLinks global = mock(ServerLinks.class);
        ServerLinks.ServerLink store = mock(ServerLinks.ServerLink.class);
        when(global.getLinks()).thenReturn(List.of()).thenReturn(List.of(store));
        Server server = serverWith(global);
        doReturn(List.of()).when(server).getOnlinePlayers();
        ServerLinksApplier applier = applier(server);
        applier.apply(List.of(new ServerLinksConfig.RawLink(null, "Store", "https://store.example.com")));

        applier.reload(List.of());

        verify(global).removeLink(store);
    }

    @Test
    void emptyListLeavesLinksUntouched() {
        Server server = mock(Server.class);
        applier(server).apply(List.of());
        verifyNoInteractions(server);
    }

    @Test
    void allMalformedListLeavesLinksUntouched() {
        Server server = mock(Server.class);
        applier(server).apply(List.of(new ServerLinksConfig.RawLink(null, null, "not-a-url")));
        verifyNoInteractions(server);
    }

    private static Server serverWith(ServerLinks links) {
        Server server = mock(Server.class);
        when(server.getServerLinks()).thenReturn(links);
        return server;
    }

    private static ServerLinksApplier applier(Server server) {
        return new ServerLinksApplier(
                server, new InlineScheduler(), noopLog(), (reader, label) -> Component.text(label + " for " + reader));
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-00000000beef"));
        when(player.getName()).thenReturn("Steve");
        return player;
    }

    private static Logger noopLog() {
        return new Logger() {
            @Override
            public void info(String m, Object... a) {}

            @Override
            public void warn(String m, Object... a) {}

            @Override
            public void error(String m, Throwable t) {}

            @Override
            public void debug(String m, Object... a) {}
        };
    }

    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
