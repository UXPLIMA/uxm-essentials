package com.uxplima.uxmessentials.shared.application.placeholder;

/** Whether a catalogued key is one key or the head of an open family. */
public enum PlaceholderShape {

    /** One key, written exactly as an operator types it: {@code homes_count}. */
    FIXED,

    /** A key with an open segment, written with its placeholder visible: {@code kit_cost_<kit>}. */
    FAMILY
}
