package com.uxplima.uxmessentials.worlds.domain;

/**
 * How a click in the GUI world editor moves a property's value: a small step forward or backward, a
 * large step (for numeric properties), or a reset to the property default.
 */
public enum CycleAction {
    FORWARD,
    BACKWARD,
    FORWARD_BIG,
    BACKWARD_BIG,
    CLEAR
}
