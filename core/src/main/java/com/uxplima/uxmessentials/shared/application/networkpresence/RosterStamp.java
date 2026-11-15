package com.uxplima.uxmessentials.shared.application.networkpresence;

/**
 * Orders the presence announcements of one backend. Announcements can arrive out of order or twice, so a receiver
 * compares sequences within one {@code session} (per player for a join or quit, and against the last roster) and
 * treats a newer {@code session} as a restart that starts over.
 *
 * @param session identifies one run of the announcing backend
 * @param sequence grows by one with every announcement the session makes
 */
public record RosterStamp(long session, long sequence) {}
