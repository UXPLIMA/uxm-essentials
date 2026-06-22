package com.uxplima.uxmessentials.communication.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.communication.application.port.AnnouncementStore;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.communication.domain.StoredAnnouncement;

/**
 * Widens the announcer's source from the file-managed {@link AnnouncerConfig} alone to that config <em>plus</em> the
 * enabled, editor-managed announcements held in the {@link AnnouncementStore}. The config set stays backward
 * compatible (an operator who never touches the editor sees no change), and the store set is the GUI-managed
 * surface; a disabled store announcement is excluded here, so toggling enabled in the editor takes an announcement
 * in or out of the rotation on the next announcer tick with no reload.
 *
 * <p>This widens only the source set: the merged config carries the same default interval, min-players gate, and
 * ordering as the file config, so the existing rotation, condition, and channel behaviour is unchanged. When a
 * store announcement's id collides with a config one, the store version wins — the editor is the authoritative
 * surface for an id it manages, and a collision is an operator editing an id that also exists in the file, where
 * the live in-game edit is the one they expect to see. The merged announcement list preserves config order first,
 * then appends the store ids the config did not already carry.
 *
 * <p>Pure: it reads the store through the port and combines value objects, holding no Bukkit dependency, so the
 * supplier it builds is the seam both {@code NextAnnouncement} (the rotation) and the adapter's override-loop timer
 * read through.
 */
public final class MergeAnnouncements {

    private final AnnouncementStore store;

    public MergeAnnouncements(AnnouncementStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * The merged config: {@code config}'s announcements first (with any whose id a store announcement reuses
     * replaced by the store version), then the remaining enabled store announcements appended. The interval,
     * min-players gate, and ordering are carried straight from {@code config}. A config with no announcements and no
     * enabled store announcements yields an empty config that never fires, exactly as the file config would.
     */
    public AnnouncerConfig merge(AnnouncerConfig config) {
        Objects.requireNonNull(config, "config");
        Map<String, Announcement> stored = new LinkedHashMap<>();
        for (StoredAnnouncement announcement : store.enabled()) {
            stored.put(announcement.id(), announcement.toAnnouncement());
        }
        List<Announcement> merged = new ArrayList<>();
        for (Announcement fromConfig : config.announcements()) {
            // A store announcement under the same id supersedes the file one; otherwise keep the file announcement.
            merged.add(stored.getOrDefault(fromConfig.id(), fromConfig));
        }
        for (Announcement fromStore : stored.values()) {
            if (config.announcements().stream().noneMatch(a -> a.id().equals(fromStore.id()))) {
                merged.add(fromStore);
            }
        }
        if (merged.isEmpty()) {
            return AnnouncerConfig.empty();
        }
        return new AnnouncerConfig(config.defaultInterval(), config.minOnlinePlayers(), config.ordering(), merged);
    }
}
