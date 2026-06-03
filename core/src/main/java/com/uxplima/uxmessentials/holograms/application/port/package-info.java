/**
 * The holograms context's outbound ports: {@code HologramRepository} for durable, server-wide hologram
 * storage (the name row plus its ordered line rows). The application depends only on this interface; the
 * jOOQ repository in the persistence adapter implements it.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.holograms.application.port;
