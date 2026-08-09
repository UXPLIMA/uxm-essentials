package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.adapter.outbound.api.HomeEventBridges;
import com.uxplima.uxmessentials.homes.adapter.outbound.api.HomeVetoBridges;
import org.jspecify.annotations.NullMarked;

/**
 * The one place every context's event bridges are installed.
 *
 * <p>Each context owns its own mapping class, next to the facts it maps, but they are all installed from here rather
 * than from each module's wiring. Two reasons. The bridge is cross-cutting infrastructure, not a module feature: it
 * has no state, holds nothing, and registering it costs one map entry per event. And a disabled module publishes no
 * facts at all, since its use cases never run, so gating registration on the module would add a moving part that
 * changes nothing observable.
 */
@NullMarked
public final class EventBridges {

    private EventBridges() {}

    /** Install every context's bridges into {@code registry}, in context order. */
    public static void installAll(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        HomeEventBridges.register(registry);
    }

    /** Install every context's veto mappings into {@code registry}, in context order. */
    public static void installAllVetoes(VetoRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        HomeVetoBridges.register(registry);
    }
}
