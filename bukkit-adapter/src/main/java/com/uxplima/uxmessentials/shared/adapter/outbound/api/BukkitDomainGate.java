package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.Objects;

import org.bukkit.plugin.PluginManager;

import com.uxplima.uxmessentials.api.bukkit.event.UxmCancellableEvent;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.DomainProposal;
import org.jspecify.annotations.NullMarked;

/**
 * Puts the veto question to the rest of the server as a cancellable Bukkit event.
 *
 * <p>The answer has to come back before the use case can continue, so unlike the notification bridge this fires on
 * the calling thread rather than hopping to a region. That is why the pre-events are documented as asynchronous and
 * why their listeners must not touch the Bukkit API.
 *
 * <p>Two ways of saying yes, and they matter for the cost of this on a server with no consumer plugin. A proposal
 * nothing published can refuse never reaches an event at all, and one whose event has no registered listener is
 * answered from the handler list without building anything. Either way a veto point costs one map lookup.
 *
 * <p>A listener that throws is a bug in that listener, not a reason to fail the player's action: the throw is logged
 * with the proposal that caused it and the action proceeds, per the {@link DomainGate} contract that this fails open.
 */
@NullMarked
public final class BukkitDomainGate implements DomainGate {

    private final VetoRegistry registry;
    private final PluginManager plugins;
    private final Logger log;

    public BukkitDomainGate(VetoRegistry registry, PluginManager plugins, Logger log) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.plugins = Objects.requireNonNull(plugins, "plugins");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean allows(DomainProposal proposal) {
        Objects.requireNonNull(proposal, "proposal");
        VetoRegistry.Entry<DomainProposal> entry = registry.lookup(proposal);
        if (entry == null || !entry.hasListeners()) {
            return true;
        }
        UxmCancellableEvent event;
        try {
            event = entry.mapper().apply(proposal);
        } catch (RuntimeException failure) {
            log.error("could not map " + proposal.getClass().getSimpleName() + " to its api event", failure);
            return true;
        }
        try {
            plugins.callEvent(event);
        } catch (RuntimeException failure) {
            log.error(
                    "a listener failed while being asked about "
                            + proposal.getClass().getSimpleName(),
                    failure);
            return true;
        }
        return !event.isCancelled();
    }
}
