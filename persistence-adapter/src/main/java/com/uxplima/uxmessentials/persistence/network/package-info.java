/**
 * The cross-server bus's Redis transport. {@link com.uxplima.uxmessentials.persistence.network.RedisBusTransport}
 * implements the core {@code BusTransport} SPI over Redis pub/sub, carrying the full {@code NetworkMessageCodec}
 * frame bytes between backends with no Velocity proxy in the path; the
 * {@link com.uxplima.uxmessentials.persistence.network.NetworkTransports} factory builds it from configuration
 * so the consuming bukkit-adapter never names a Jedis type. Jedis lives in this module already (it backs the
 * economy cache-invalidation in the sibling {@code economy} package), so the bus's Redis machinery sits here
 * alongside it rather than pulling Jedis into the bukkit-adapter's compile classpath.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.network;
