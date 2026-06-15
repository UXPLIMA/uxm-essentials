package com.uxplima.uxmessentials.npc.application.port;

import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;

/**
 * Outbound port the use cases drive to keep the in-world rendering in step with the stored model. The renderer
 * adapter realises it over the uxmLib fake-player packet stack: {@link #render} spawns (or re-spawns) the NPC
 * for every eligible viewer, and {@link #despawn} removes it from every viewer. The application owns when the
 * world should change; the adapter owns how (per-viewer packets on the right region thread). Keeping the seam
 * here lets the use cases be unit-tested against a fake view that records the calls, with no Bukkit.
 */
public interface NpcView {

    /** Spawn or re-spawn {@code npc} for every eligible viewer (a create, a move, or a re-skin). */
    void render(Npc npc);

    /** Remove the NPC under {@code name} from every viewer (a delete). */
    void despawn(NpcName name);
}
