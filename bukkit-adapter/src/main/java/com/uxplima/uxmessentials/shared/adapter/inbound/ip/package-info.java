/**
 * The kernel IP-history capture: one join listener ({@code IpHistoryRecorder}) tokenises the connecting address,
 * writes the association off the tick thread, and notifies the contexts watching it ({@code IpHistoryObserver}).
 * Moderation and security both read the resulting rows; neither captures a join of its own, so there is one
 * record of who connected from where rather than one per context.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.ip;
