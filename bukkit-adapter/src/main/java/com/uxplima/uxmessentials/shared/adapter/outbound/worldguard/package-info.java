/**
 * Shared WorldGuard flag adapters. {@link com.uxplima.uxmessentials.shared.adapter.outbound.worldguard.WorldGuardSetPwarpFlagRegistrar}
 * registers the {@code set-pwarp} custom flag in the load phase, and
 * {@link com.uxplima.uxmessentials.shared.adapter.outbound.worldguard.BukkitWorldGuardFlags} answers the
 * {@link com.uxplima.uxmessentials.shared.application.port.WorldGuardFlags} port by querying that flag's region state.
 *
 * <p>Both reach WorldGuard purely by reflection behind a plugin-present guard, so a server without WorldGuard loads
 * none of the {@code com.sk89q} classes they name only by string.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.worldguard;

import org.jspecify.annotations.NullMarked;
