package com.uxplima.uxmessentials.staff.application.port;

import com.uxplima.uxmessentials.staff.application.RemoteStaffChat;

/**
 * Carries staff chat to the other backends of a network. A port because it crosses a platform boundary (the
 * cross-server bus) and is the seam the multi-backend tests drive with a fake. Fire-and-forget: a bus with no path to
 * peers discards the line, and the staff here still read it.
 */
public interface StaffChatNetwork {

    /** The network of a lone server: nothing leaves it. */
    StaffChatNetwork NONE = chat -> {
        // No peers to reach.
    };

    /** Show {@code chat} to the staff on every other backend. */
    void publish(RemoteStaffChat chat);
}
