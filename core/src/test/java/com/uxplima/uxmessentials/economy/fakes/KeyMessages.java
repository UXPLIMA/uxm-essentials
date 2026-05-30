package com.uxplima.uxmessentials.economy.fakes;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A {@link Messages} fake that resolves a {@link MessageKey} to its own catalog key, so a test can assert
 * which key was rendered without a real locale catalog. Placeholders are appended so a test can additionally
 * inspect the interpolated values.
 */
public final class KeyMessages implements Messages {

    @Override
    public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return placeholders.isEmpty() ? key.key() : key.key() + placeholders;
    }
}
