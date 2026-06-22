package com.uxplima.uxmessentials.communication.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.communication.application.port.AnnouncementStore;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.communication.domain.Ordering;
import com.uxplima.uxmessentials.communication.domain.StoredAnnouncement;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.junit.jupiter.api.Test;

/**
 * The announcer's source is the file config <em>plus</em> the enabled store announcements. These cover the merge
 * contract: an enabled store announcement is included, a disabled one is excluded, the config set is preserved, a
 * store id colliding with a config id supersedes it, and the interval/gate/ordering carry through from the config.
 */
class MergeAnnouncementsTest {

    @Test
    void anEnabledStoreAnnouncementIsAddedToTheConfigSet() {
        InMemoryAnnouncementStore store = new InMemoryAnnouncementStore();
        store.save(StoredAnnouncement.fresh("stored", "from the editor"));
        MergeAnnouncements merge = new MergeAnnouncements(store);

        AnnouncerConfig merged = merge.merge(configWith("file"));

        assertThat(merged.announcements()).extracting(Announcement::id).containsExactly("file", "stored");
    }

    @Test
    void aDisabledStoreAnnouncementIsExcluded() {
        InMemoryAnnouncementStore store = new InMemoryAnnouncementStore();
        store.save(StoredAnnouncement.fresh("on", "shown"));
        store.save(StoredAnnouncement.fresh("off", "hidden").withEnabled(false));
        MergeAnnouncements merge = new MergeAnnouncements(store);

        AnnouncerConfig merged = merge.merge(configWith("file"));

        assertThat(merged.announcements()).extracting(Announcement::id).containsExactly("file", "on");
    }

    @Test
    void anEmptyStoreLeavesTheConfigSetUnchanged() {
        MergeAnnouncements merge = new MergeAnnouncements(new InMemoryAnnouncementStore());

        AnnouncerConfig merged = merge.merge(configWith("a", "b"));

        assertThat(merged.announcements()).extracting(Announcement::id).containsExactly("a", "b");
    }

    @Test
    void aStoreIdSupersedesACollidingConfigId() {
        InMemoryAnnouncementStore store = new InMemoryAnnouncementStore();
        store.save(new StoredAnnouncement(
                "shared",
                List.of("editor version"),
                Set.of(BroadcastChannel.TITLE),
                true,
                "",
                Optional.empty(),
                OptionalLong.empty()));
        MergeAnnouncements merge = new MergeAnnouncements(store);

        AnnouncerConfig merged = merge.merge(configWith("shared"));

        assertThat(merged.announcements()).singleElement().satisfies(announcement -> {
            assertThat(announcement.id()).isEqualTo("shared");
            assertThat(announcement.lines()).containsExactly("editor version");
            assertThat(announcement.channels()).containsExactly(BroadcastChannel.TITLE);
        });
    }

    @Test
    void theIntervalGateAndOrderingCarryThroughFromTheConfig() {
        InMemoryAnnouncementStore store = new InMemoryAnnouncementStore();
        store.save(StoredAnnouncement.fresh("stored", "line"));
        MergeAnnouncements merge = new MergeAnnouncements(store);
        AnnouncerConfig config =
                new AnnouncerConfig(Duration.ofSeconds(90), 3, Ordering.RANDOM, List.of(announcement("file")));

        AnnouncerConfig merged = merge.merge(config);

        assertThat(merged.defaultInterval()).isEqualTo(Duration.ofSeconds(90));
        assertThat(merged.minOnlinePlayers()).isEqualTo(3);
        assertThat(merged.ordering()).isEqualTo(Ordering.RANDOM);
    }

    @Test
    void anEmptyConfigWithOnlyAStoreAnnouncementStillRotates() {
        InMemoryAnnouncementStore store = new InMemoryAnnouncementStore();
        store.save(StoredAnnouncement.fresh("stored", "line"));
        MergeAnnouncements merge = new MergeAnnouncements(store);

        AnnouncerConfig merged = merge.merge(AnnouncerConfig.empty());

        assertThat(merged.hasAnnouncements()).isTrue();
        assertThat(merged.announcements()).extracting(Announcement::id).containsExactly("stored");
    }

    private static AnnouncerConfig configWith(String... ids) {
        List<Announcement> announcements = new ArrayList<>();
        for (String id : ids) {
            announcements.add(announcement(id));
        }
        if (announcements.isEmpty()) {
            return AnnouncerConfig.empty();
        }
        return new AnnouncerConfig(Duration.ofSeconds(60), 0, Ordering.SEQUENTIAL, announcements);
    }

    private static Announcement announcement(String id) {
        return new Announcement(
                id,
                List.of("config line"),
                DisplayCondition.always(),
                Optional.empty(),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty(),
                false);
    }

    /** A hand-rolled in-memory store, faster and clearer than a mock for the merge contract. */
    private static final class InMemoryAnnouncementStore implements AnnouncementStore {
        private final Map<String, StoredAnnouncement> rows = new ConcurrentHashMap<>();

        @Override
        public List<StoredAnnouncement> all() {
            return rows.values().stream()
                    .sorted(java.util.Comparator.comparing(StoredAnnouncement::id))
                    .toList();
        }

        @Override
        public List<StoredAnnouncement> enabled() {
            return all().stream().filter(StoredAnnouncement::enabled).toList();
        }

        @Override
        public Optional<StoredAnnouncement> find(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public boolean exists(String id) {
            return rows.containsKey(id);
        }

        @Override
        public void save(StoredAnnouncement announcement) {
            rows.put(announcement.id(), announcement);
        }

        @Override
        public boolean delete(String id) {
            return rows.remove(id) != null;
        }
    }
}
