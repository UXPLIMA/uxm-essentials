package com.uxplima.uxmessentials.worlds.application.port;

import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSettings;

/**
 * Applies a world's settings, gamerules, and spawn onto the live world. A no-op when the named world
 * is not loaded (the settings re-apply the next time it loads). The caller is responsible for running
 * this on the global region thread (via the {@code Scheduler} port), since it mutates live world state.
 */
public interface WorldSettingApplier {

    void apply(WorldName name, WorldSettings settings);
}
