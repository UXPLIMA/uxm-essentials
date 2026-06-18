package com.uxplima.uxmessentials.worlds.application;

import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;

final class TestSupport {
    private TestSupport() {}

    static WorldNotifier notifier() {
        Messages messages = (v, key, ph) -> key.key();
        MessageSink sink = (v, text) -> {};
        return new WorldNotifier(messages, sink);
    }
}
