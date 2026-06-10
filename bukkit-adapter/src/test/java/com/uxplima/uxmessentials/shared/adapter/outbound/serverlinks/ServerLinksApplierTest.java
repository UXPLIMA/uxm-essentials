package com.uxplima.uxmessentials.shared.adapter.outbound.serverlinks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.bukkit.Server;
import org.bukkit.ServerLinks;

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
        verify(links).addLink(Component.text("Discord"), URI.create("https://discord.gg/x"));
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
        return new ServerLinksApplier(server, new InlineScheduler(), noopLog());
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
