package com.uxplima.uxmessentials.api.view;

/**
 * What a hologram is made of. A hologram is one of these and not a mixture, so the type says which of
 * {@link UxmHologram}'s content fields carries anything.
 */
public enum UxmHologramType {

    /** Floating lines of text, the common kind, whose lines are {@link UxmHologram#lines()}. */
    TEXT,

    /** A single floating item. */
    ITEM,

    /** A single floating block. */
    BLOCK,

    /** A floating player head. */
    HEAD,

    /** A floating entity. */
    ENTITY
}
