/**
 * The regions context's Bukkit adapters: {@link com.uxplima.uxmessentials.regions.adapter.RegionsWiring} wires the
 * soft-dependency {@code RegionService} (the reflective WorldGuard implementation, or the no-op fallback) and the
 * {@code /regions} command over the injected kernel ports and the shared menu engine. The WorldGuard SDK is named
 * only under {@code adapter/outbound}; nothing here reaches the pure core.
 */
@NullMarked
package com.uxplima.uxmessentials.regions.adapter;

import org.jspecify.annotations.NullMarked;
