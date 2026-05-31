/**
 * The communication context's bukkit-adapter wiring: {@code CommunicationWiring} constructs the
 * connection-message resolution, announcer rotation, and {@code /broadcasttoggle} use cases over the kernel ports
 * and the context's own PDC opt-out store, sequence counters, and random source, then produces the Brigadier
 * commands (static {@code /broadcasttoggle} plus the config-derived info-page commands), the join/quit/death
 * listeners, and the self-rescheduling announcer timer. {@code CommunicationSettings} loads the operator content
 * from {@code communication.conf} once and swaps it atomically on reload; {@code CommunicationContentCodec} parses
 * the HOCON tree into the immutable {@code CommunicationContent} the use cases read. The {@code Plugin} handle is
 * needed only for the PDC namespace and the data-folder path; the adapters take the {@code Plugin} interface and
 * the kernel ports, nothing from bootstrap.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.communication.adapter;
