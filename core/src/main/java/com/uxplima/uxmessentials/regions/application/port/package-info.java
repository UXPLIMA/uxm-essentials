/**
 * The regions context's outbound port: {@link com.uxplima.uxmessentials.regions.application.port.RegionService}, the
 * one seam over WorldGuard's region API. The context reasons entirely through this interface and the regions domain
 * value objects, so the WorldGuard SDK is named only by the outbound adapter that implements it. Pure Java: no
 * Bukkit, Paper, Kyori, or WorldGuard.
 */
@NullMarked
package com.uxplima.uxmessentials.regions.application.port;

import org.jspecify.annotations.NullMarked;
