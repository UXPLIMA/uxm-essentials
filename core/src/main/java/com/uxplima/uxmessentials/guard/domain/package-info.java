/**
 * Pure domain of the guard bounded context: region protection without WorldGuard.
 *
 * <p>A {@code Region} is a {@code Shape} in a world with protection {@code Rule}s and separate
 * {@code Behaviours}. A rule names a node of the {@code ActionTree} (a rule on {@code use} covers
 * {@code use.door}), a {@code Target} (everyone, a region role, a permission, a condition) and an
 * {@code Effect}. Regions overlap by precedence: priority first, then the inner region, and a tie denies.
 * The compiled, lock-free form the listeners read lives in {@code guard.domain.engine}.
 *
 * <p>No Bukkit, Paper, Kyori or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.guard.domain;
