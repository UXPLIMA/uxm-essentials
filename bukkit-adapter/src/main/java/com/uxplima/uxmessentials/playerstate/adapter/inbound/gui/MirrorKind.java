package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import org.jspecify.annotations.NullMarked;

/**
 * Which container a managed mirror window shows. The two kinds differ only in the spec they open and how many slots
 * their content region holds; the online and offline paths both use both, so the pair lives here rather than inside
 * either view.
 */
@NullMarked
enum MirrorKind {

    /** The target's full inventory: main slots, armour and offhand, laid out by {@link InvseeLayout}. */
    INVENTORY(MirrorWindow.INVSEE_SPEC_ID, MirrorWindow.INVSEE_REGION, InvseeLayout.SLOT_COUNT),

    /** The target's ender chest: a flat container, laid out by {@link EnderLayout}. */
    ENDER(MirrorWindow.ENDERSEE_SPEC_ID, MirrorWindow.ENDERSEE_REGION, EnderLayout.SIZE);

    private final String specId;
    private final String regionId;
    private final int slotCount;

    MirrorKind(String specId, String regionId, int slotCount) {
        this.specId = specId;
        this.regionId = regionId;
        this.slotCount = slotCount;
    }

    /** The menu spec this kind of mirror opens. */
    String specId() {
        return specId;
    }

    /** The content region inside that spec whose slots hold the mirrored items. */
    String regionId() {
        return regionId;
    }

    /** How many item slots the mirror carries; the region must declare exactly this many. */
    int slotCount() {
        return slotCount;
    }
}
