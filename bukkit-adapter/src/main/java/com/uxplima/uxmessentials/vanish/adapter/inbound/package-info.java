/**
 * The vanish context's inbound adapters: the {@code /vanish} Brigadier command and the join/quit lifecycle listener
 * that keep the vanish view coherent. Each maps a platform event onto the {@code ToggleVanish} use case or the vanish
 * authority; neither holds logic of its own.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vanish.adapter.inbound;
