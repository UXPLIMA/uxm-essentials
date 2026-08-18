/**
 * The skin context's Bukkit adapter: {@link com.uxplima.uxmessentials.skin.adapter.SkinWiring} constructs the jOOQ
 * skin repository, the MineSkin upload service, the Geyser-backed Bedrock lookup and the Paper profile view over
 * the injected kernel ports, and produces the {@code /skin} command together with the pre-login listener that
 * dresses a player on the way in. Everything Bukkit-shaped about a skin lives here; the use cases behind it know
 * only ports.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.skin.adapter;
