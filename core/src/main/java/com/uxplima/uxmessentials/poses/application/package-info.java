/**
 * The poses context's application layer: the {@link com.uxplima.uxmessentials.poses.application.PoseModule} feature
 * module (ships enabled, no persistence), the {@link com.uxplima.uxmessentials.poses.application.PoseSessions}
 * registry that is the single source of truth for who is posing, the typed
 * {@link com.uxplima.uxmessentials.poses.application.PosesConfig} view of {@code modules/poses/config.conf}, and the
 * {@link com.uxplima.uxmessentials.poses.application.PosesMessageKey} catalog. The pose use cases and their command
 * surface land here as the later behaviour phases arrive; Phase 0 is the wired-but-inert skeleton. Pure Java: no
 * Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.poses.application;

import org.jspecify.annotations.NullMarked;
