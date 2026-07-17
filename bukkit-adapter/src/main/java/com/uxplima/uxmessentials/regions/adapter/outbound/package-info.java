/**
 * The regions context's outbound adapters: the {@code RegionService} implementations. The
 * {@link com.uxplima.uxmessentials.regions.adapter.outbound.WorldGuardRegionService} is the sole place the
 * WorldGuard SDK is named, reached purely by reflection behind a plugin-present guard, and the
 * {@link com.uxplima.uxmessentials.regions.adapter.outbound.NoWorldGuardRegionService} is the no-op that stands in
 * when WorldGuard is absent so the module stays inert.
 */
@NullMarked
package com.uxplima.uxmessentials.regions.adapter.outbound;

import org.jspecify.annotations.NullMarked;
