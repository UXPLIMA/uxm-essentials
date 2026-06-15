package com.uxplima.uxmessentials.npc.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /npc list}: list every server-wide NPC in stored creation order. NPCs are an operator surface (the
 * command is gated as a whole), so the list is unfiltered — every NPC is shown. The NPCs are returned for the
 * adapter to render, and the header / per-entry / empty feedback is pushed through the notifier so all text
 * resolves from {@link NpcMessageKey}.
 */
public final class ListNpcs {

    private final NpcRepository repository;
    private final NpcNotifier notifier;

    public ListNpcs(NpcRepository repository, NpcNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** The NPCs in stored creation order, also pushing the header/entries (or the empty notice). */
    public List<Npc> list(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        List<Npc> all = repository.all();
        if (all.isEmpty()) {
            notifier.send(viewer, NpcMessageKey.NPC_LIST_EMPTY);
            return all;
        }
        notifier.send(viewer, NpcMessageKey.NPC_LIST_HEADER, Map.of("count", Integer.toString(all.size())));
        for (Npc npc : all) {
            notifier.send(
                    viewer,
                    NpcMessageKey.NPC_LIST_ENTRY,
                    Map.of(
                            "name",
                            npc.name().value(),
                            "world",
                            npc.location().world().name()));
        }
        return all;
    }
}
