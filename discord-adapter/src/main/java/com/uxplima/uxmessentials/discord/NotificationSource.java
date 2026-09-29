package com.uxplima.uxmessentials.discord;

/**
 * The host plugin's notification feed, as the bridge reads it. {@link HostAuditSource} is the one implementation: it
 * reads the host's {@code AuditFeed}, which the host registers in {@code ServicesManager}, and fans every audit and
 * economy line out as an {@link AuditNotice}. The bridge subscribes once the gateway is ready and unsubscribes on
 * disable (docs/09-deployment.md Path C). This port is the bridge's own and never crosses to the host: a type both
 * jars name lives in {@code :api}.
 *
 * <p>Keeping the subscription behind this thin port is what lets the forwarding pipeline be unit-tested with a
 * fake source and a fake gateway, with no live JDA connection and no MockBukkit server (CLAUDE.md GROUND RULE).
 */
public interface NotificationSource {

    /** A registered consumer of host notifications. */
    @FunctionalInterface
    interface Listener {
        /** Called once per host notification, on the host's emitting thread (never the bridge's concern). */
        void onNotice(AuditNotice notice);
    }

    /**
     * Register a listener for host notifications. Returns a {@link Subscription} the caller closes to stop
     * receiving; registering the same listener twice is the caller's responsibility to avoid.
     */
    Subscription subscribe(Listener listener);

    /** Handle to a live subscription; closing it stops delivery. Idempotent. */
    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
