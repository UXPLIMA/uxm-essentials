/**
 * The event stream: one path that stops being HTTP and stays open.
 *
 * <p>RFC 6455 written out rather than pulled in, because the shape needed here is small (whole text messages one
 * way; text, ping, pong and close the other) and because an optional add-on that drags a WebSocket library into a
 * Paper classloader is a version conflict waiting for the first server that already has one.
 *
 * <p>Nothing is delivered until a subscription asks for it. A connection that forgot to subscribe hears silence,
 * which is the honest answer; the alternative, handing it every event on the server, is a bandwidth bill rather
 * than a convenience.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.rest.socket;
