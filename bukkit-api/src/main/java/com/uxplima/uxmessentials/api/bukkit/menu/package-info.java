/**
 * The published menu-engine surface: {@code MenuApi} registers custom actions, requirements, placeholders, list
 * sources and icon providers, and {@code MenuView} / {@code MenuClick} are what a registered handler is handed.
 *
 * <p>These types name only JDK and Bukkit types on purpose. The engine's own spec model and runtime contexts stay
 * internal so they remain free to change; each is adapted onto the interfaces here at the boundary.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.api.bukkit.menu;
