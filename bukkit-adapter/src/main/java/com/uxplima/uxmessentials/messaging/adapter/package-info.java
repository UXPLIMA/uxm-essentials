/**
 * The messaging context's bukkit-adapter wiring: {@code MessagingWiring} constructs the use cases over the
 * kernel ports and the persistence DSL, the jOOQ mail/ignore stores, the in-memory reply and socialspy
 * stores, the PDC {@code /msgtoggle} store, the {@code canSee}-based vanish gate (soft-coupled to presence),
 * and the soft-coupled mute gate (bound to {@code MutePolicy.NEVER} until moderation lands). It produces the
 * Brigadier commands the plugin registers and the self-rescheduling mail-expiry sweep. The {@code Plugin}
 * handle stays inside bootstrap; the adapters take only the {@code Plugin} interface and the kernel ports.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.messaging.adapter;
