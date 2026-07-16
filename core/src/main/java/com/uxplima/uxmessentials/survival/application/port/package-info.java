/**
 * The survival context's application ports. {@code RandomSource} is the bounded RNG the head-drop roll draws through so
 * the drop decision stays pure and deterministic under test; the adapter binds a seeded {@code java.util.random}
 * source in the bukkit module.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.survival.application.port;
