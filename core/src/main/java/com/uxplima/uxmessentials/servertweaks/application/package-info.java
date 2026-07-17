/**
 * The server-tweaks context's application layer: the {@link com.uxplima.uxmessentials.servertweaks.application.ServerTweaksModule}
 * feature-module identity/enable gate and the {@link com.uxplima.uxmessentials.servertweaks.application.ServerTweaksConfig}
 * typed view of {@code modules/servertweaks/config.conf}. Both are pure application code — the actual Bukkit-facing
 * effects (the F3-brand plugin message, the Log4j2 console filter) live in the adapter. No Bukkit, Paper, Kyori, or
 * SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.servertweaks.application;

import org.jspecify.annotations.NullMarked;
