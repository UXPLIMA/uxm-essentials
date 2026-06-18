/**
 * The worlds context's adapter composition root: {@code WorldsWiring}, which news up the cached
 * repository, the {@code BukkitWorldEngine}, the use cases and the {@code WorldsServices} the inbound
 * commands consume, and schedules the enable-time reconcile; and {@code WorldsServices}, the started
 * module's use-case holder plus the off-tick import-folder snapshot the {@code /worlds import}
 * tab-completion reads. The inbound commands live in {@code .inbound.command}, the outbound Bukkit
 * adapter in {@code .outbound}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.worlds.adapter;
