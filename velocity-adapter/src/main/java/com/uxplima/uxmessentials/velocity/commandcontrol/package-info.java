/**
 * The proxy-side command-control adapter: the Velocity inbound layer that hides and gates PROXY-NATIVE
 * commands ({@code /server}, {@code /glist}, {@code /send}, {@code /find}, proxy plugins' commands) the
 * way PlHidePro does on the proxy. It reuses the pure {@code com.uxplima.uxmessentials.commandcontrol}
 * domain from {@code :core} (rule sets, hide policy, the command-spam rate limiter) so the proxy layer
 * decides allow/deny/hide exactly as the backend adapter does, and only the Velocity plumbing lives here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.velocity.commandcontrol;
