package com.uxplima.uxmessentials.api.view;

/** Which way a teleport request would move somebody, which is what separates {@code /tpa} from {@code /tpahere}. */
public enum UxmTeleportRequestDirection {

    /** {@code /tpa}: the requester goes to the target. */
    TO_TARGET,

    /** {@code /tpahere}: the target comes to the requester. */
    TO_REQUESTER
}
