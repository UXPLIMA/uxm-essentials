package com.uxplima.uxmessentials.shared.adapter.inbound.api;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.api.action.UxmActions;
import com.uxplima.uxmessentials.api.action.UxmEconomyActions;
import com.uxplima.uxmessentials.api.action.UxmHomeActions;
import com.uxplima.uxmessentials.api.action.UxmKitActions;
import com.uxplima.uxmessentials.api.action.UxmMessagingActions;
import com.uxplima.uxmessentials.api.action.UxmModerationActions;
import com.uxplima.uxmessentials.api.action.UxmPlayerStateActions;
import com.uxplima.uxmessentials.api.action.UxmPresenceActions;
import com.uxplima.uxmessentials.api.action.UxmTeleportActions;
import com.uxplima.uxmessentials.api.action.UxmVanishActions;
import com.uxplima.uxmessentials.api.action.UxmVoteActions;
import com.uxplima.uxmessentials.api.action.UxmWarpActions;
import com.uxplima.uxmessentials.api.action.UxmWorldsActions;
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

    @Override
    public Optional<UxmModerationActions> moderation() {
        return contexts.find(UxmModerationActions.class, source);
    }

    @Override
    public Optional<UxmPlayerStateActions> playerState() {
        return contexts.find(UxmPlayerStateActions.class, source);
    }

    @Override
    public Optional<UxmPresenceActions> presence() {
        return contexts.find(UxmPresenceActions.class, source);
    }

    @Override
    public Optional<UxmVanishActions> vanish() {
        return contexts.find(UxmVanishActions.class, source);
    }

    @Override
    public Optional<UxmTeleportActions> teleport() {
        return contexts.find(UxmTeleportActions.class, source);
    }

    @Override
    public Optional<UxmWorldsActions> worlds() {
        return contexts.find(UxmWorldsActions.class, source);
    }

    @Override
    public Optional<UxmVoteActions> vote() {
        return contexts.find(UxmVoteActions.class, source);
    }

    @Override
    public Optional<UxmMessagingActions> messaging() {
        return contexts.find(UxmMessagingActions.class, source);
    }
}
