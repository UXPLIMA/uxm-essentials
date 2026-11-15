package com.uxplima.uxmessentials.npc.application;

import java.util.Objects;

import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.domain.LocalServer;

/**
 * The view every NPC render goes through, so an NPC scoped to other servers never shows here. A render of an NPC
 * this server does not cover takes it off instead: a scope changed here or on a peer leaves the world on the render
 * of the changed snapshot.
 */
public final class ScopedNpcView implements NpcView {

    private final NpcView delegate;
    private final LocalServer local;

    public ScopedNpcView(NpcView delegate, LocalServer local) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.local = Objects.requireNonNull(local, "local");
    }

    @Override
    public void render(Npc npc) {
        Objects.requireNonNull(npc, "npc");
        if (npc.shownOn(local)) {
            delegate.render(npc);
        } else {
            delegate.despawn(npc.name());
        }
    }

    @Override
    public void despawn(NpcName name) {
        delegate.despawn(name);
    }
}
