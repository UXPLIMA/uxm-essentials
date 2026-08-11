package com.uxplima.uxmessentials.shared.adapter.inbound.ip;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Notified once a join's IP association has been written, on the same off-tick task that wrote it. A context that
 * reacts to an address (security's per-address account cap and its shares-an-address staff notice) registers here
 * rather than capturing the join itself, so the write always happens before the read that depends on it and there
 * is still only one capture.
 */
@FunctionalInterface
public interface IpHistoryObserver {

    /** Called after {@code player}'s association with {@code ipToken} has been recorded. */
    void onRecorded(PlayerRef player, String ipToken);
}
