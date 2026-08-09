package com.uxplima.uxmessentials.api.view;

/** What class of entity a cleanup removed. Never players, under any of these. */
public enum UxmPurgeCategory {

    /** Hostile mobs only. */
    MONSTERS,

    /** One named entity type. */
    NAMED_TYPE,

    /** Everything removable: drops, mobs, projectiles. */
    ALL_ENTITIES
}
