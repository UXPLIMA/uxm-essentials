package com.uxplima.uxmessentials.shared.adapter.inbound.api;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.api.action.UxmActions;
import com.uxplima.uxmessentials.api.action.UxmEconomyActions;
import com.uxplima.uxmessentials.api.action.UxmHomeActions;
import com.uxplima.uxmessentials.api.action.UxmKitActions;
import com.uxplima.uxmessentials.api.action.UxmWarpActions;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ActionContexts;
import org.jspecify.annotations.NullMarked;

/**
 * The write surface as handed to one plugin.
 *
 * <p>Built per call rather than once, because it carries who is asking. The registry behind it is shared and
 * concurrent, so building one costs a field assignment and the surfaces themselves are made on demand.
 */
@NullMarked
public final class UxmActionsImpl implements UxmActions {

    private final String source;
    private final ActionContexts contexts;

    public UxmActionsImpl(String source, ActionContexts contexts) {
        this.source = Objects.requireNonNull(source, "source");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    @Override
    public Optional<UxmEconomyActions> economy() {
        return contexts.find(UxmEconomyActions.class, source);
    }

    @Override
    public Optional<UxmHomeActions> homes() {
        return contexts.find(UxmHomeActions.class, source);
    }

    @Override
    public Optional<UxmWarpActions> warps() {
        return contexts.find(UxmWarpActions.class, source);
    }

    @Override
    public Optional<UxmKitActions> kits() {
        return contexts.find(UxmKitActions.class, source);
    }
}
