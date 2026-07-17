/**
 * The vanish context's adapter wiring. {@link com.uxplima.uxmessentials.vanish.adapter.VanishWiring} constructs the
 * in-memory vanish authority, the packet/Bukkit hide-show view, and the {@code ToggleVanish} use case over the kernel
 * ports, and produces the {@code /vanish} command and the join/quit listener the plugin registers. It exposes the
 * store and the toggle so bootstrap can point the messaging/nametags vanish gates and staff-mode vanish at the one
 * authority.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vanish.adapter;
