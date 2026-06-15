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

    /**
     * Reflect the current {@code npc} snapshot for every eligible viewer (a create, a move, or a re-skin): an in-range
     * viewer is (re-)rendered with the new state, an out-of-range viewer that had it is removed. Already-shown viewers
     * are re-rendered too, so an edit lands immediately rather than waiting for them to leave and re-enter range.
     */
    void render(Npc npc);

    /** Remove the NPC under {@code name} from every viewer (a delete). */
    void despawn(NpcName name);
}
