/**
 * The npc context's adapter wiring: {@code NpcWiring} constructs the renderer (over the uxmLib NPC packet
 * stack), the cached jOOQ repository, the use cases and the {@code /npc} command from the kernel ports and the
 * persistence DSL, and {@code NpcServices} bundles the use cases the command shares. The inbound command and
 * listener live under {@code adapter.inbound}, the packet renderer and skin reader under {@code adapter.outbound}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.npc.adapter;
