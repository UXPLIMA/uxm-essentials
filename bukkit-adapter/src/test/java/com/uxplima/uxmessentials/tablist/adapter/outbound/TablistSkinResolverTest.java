package com.uxplima.uxmessentials.tablist.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.junit.jupiter.api.Test;

/**
 * Unit-tests the {@link TablistSkinResolver}'s three paths in isolation: a {@code texture:} source is passed straight
 * through, an online player's texture is read inline with no fetch, and an offline name returns no skin now but schedules
 * exactly one async fetch whose result the cache then serves on the next call. The seam is a fake profile source and a
 * deferred scheduler, so no live server is needed.
 */
class TablistSkinResolverTest {

    @Test
    void aTextureSourcePassesStraightThroughWithNoFetch() {
        FakeProfiles profiles = new FakeProfiles();
        DeferredScheduler scheduler = new DeferredScheduler();
        TablistSkinResolver resolver = new TablistSkinResolver(profiles, scheduler);

        Optional<TabSkin> skin = resolver.resolve(new TablistSkinSource.Texture("dmFsdWU=", Optional.of("sig")));

        assertThat(skin).isPresent();
        assertThat(skin.get().textureValue()).isEqualTo("dmFsdWU=");
        assertThat(skin.get().signature()).isEqualTo("sig");
        assertThat(scheduler.pending).isEmpty();
    }

    @Test
    void anOnlinePlayerTextureIsReadInlineWithNoFetch() {
        FakeProfiles profiles = new FakeProfiles();
        profiles.online.put("Target", new TabSkin("tex", null));
        DeferredScheduler scheduler = new DeferredScheduler();
        TablistSkinResolver resolver = new TablistSkinResolver(profiles, scheduler);

        Optional<TabSkin> skin = resolver.resolve(new TablistSkinSource.PlayerName("Target"));

        assertThat(skin).isPresent();
        assertThat(skin.get().textureValue()).isEqualTo("tex");
        assertThat(scheduler.pending).isEmpty();
    }

    @Test
    void anOfflineNameSchedulesOneFetchThenServesFromCache() {
        FakeProfiles profiles = new FakeProfiles();
        profiles.fetchable.put("notch", new TabSkin("notchtex", null));
        DeferredScheduler scheduler = new DeferredScheduler();
        TablistSkinResolver resolver = new TablistSkinResolver(profiles, scheduler);
        TablistSkinSource source = new TablistSkinSource.PlayerName("Notch");

        // First call: no skin yet, exactly one fetch scheduled.
        assertThat(resolver.resolve(source)).isEmpty();
        assertThat(scheduler.pending).hasSize(1);

        // A second call while the fetch is in flight does not schedule a duplicate.
        assertThat(resolver.resolve(source)).isEmpty();
        assertThat(scheduler.pending).hasSize(1);

        // Run the fetch; the cache now serves the texture.
        scheduler.runAll();
        Optional<TabSkin> skin = resolver.resolve(source);
        assertThat(skin).isPresent();
        assertThat(skin.get().textureValue()).isEqualTo("notchtex");
        // No further fetch is scheduled once the value is cached.
        assertThat(scheduler.pending).isEmpty();
    }

    @Test
    void aMissedOfflineFetchIsCachedAsNoSkin() {
        FakeProfiles profiles = new FakeProfiles();
        DeferredScheduler scheduler = new DeferredScheduler();
        TablistSkinResolver resolver = new TablistSkinResolver(profiles, scheduler);
        TablistSkinSource source = new TablistSkinSource.PlayerName("Ghost");

        assertThat(resolver.resolve(source)).isEmpty();
        scheduler.runAll();
        // The miss is cached, so a later call still returns empty and schedules no new fetch.
        assertThat(resolver.resolve(source)).isEmpty();
        assertThat(scheduler.pending).isEmpty();
    }

    private static final class FakeProfiles implements MojangProfileSource {
        private final Map<String, TabSkin> online = new HashMap<>();
        private final Map<String, TabSkin> fetchable = new HashMap<>();

        @Override
        public Optional<TabSkin> onlineTexture(String name) {
            return Optional.ofNullable(online.get(name));
        }

        @Override
        public Optional<TabSkin> fetchTexture(String name) {
            return Optional.ofNullable(fetchable.get(name.toLowerCase(Locale.ROOT)));
        }
    }

    private static final class DeferredScheduler implements Scheduler {
        private final List<Runnable> pending = new ArrayList<>();

        @Override
        public void async(Runnable task) {
            pending.add(task);
        }

        void runAll() {
            List<Runnable> snapshot = List.copyOf(pending);
            pending.clear();
            snapshot.forEach(Runnable::run);
        }

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
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
