package com.uxplima.uxmessentials.api.view;

/** How much of a player's traffic an ignore entry suppresses. */
public enum UxmIgnoreScope {

    /** Private messages and mail alike, which is what {@code /ignore} creates. */
    ALL,

    /** Private messages only; mail still arrives. */
    MESSAGES,

    /** Mail only; private messages still arrive. */
    MAIL
}
