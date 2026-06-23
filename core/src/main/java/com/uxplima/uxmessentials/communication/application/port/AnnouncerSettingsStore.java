package com.uxplima.uxmessentials.communication.application.port;

import com.uxplima.uxmessentials.communication.domain.AnnouncerSettings;

/**
 * The durable home of the global announcer-settings override the {@code /announce} editor's settings screen writes:
 * the operator's in-game override of the file's default interval and min-players gate. A single record (there is
 * exactly one global settings row), persisted so it survives a restart and a world rollback.
 *
 * <p>The announcer's merge step reads {@link #load()} each tick to fold the override into the file config, and the
 * settings screen writes {@link #save(AnnouncerSettings)} when an operator changes a value. A fresh, never-edited
 * store reads as {@link AnnouncerSettings#unset()} so both globals defer to the file until the screen sets one.
 */
public interface AnnouncerSettingsStore {

    /** The persisted global settings, or {@link AnnouncerSettings#unset()} when nothing has been set. */
    AnnouncerSettings load();

    /** Persist {@code settings} as the global override, replacing the previous one. */
    void save(AnnouncerSettings settings);
}
