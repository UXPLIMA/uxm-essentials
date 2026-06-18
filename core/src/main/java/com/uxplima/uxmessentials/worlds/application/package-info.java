/**
 * The worlds context's use cases and outbound ports. The use cases orchestrate world creation, import,
 * load/unload, and deletion through the context's ports, gate the operator commands through the shared
 * permission seam, and render every outcome through the {@link
 * com.uxplima.uxmessentials.worlds.application.WorldsMessageKey} catalog — no inline player-facing
 * literal appears anywhere in the context. No Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.worlds.application;
