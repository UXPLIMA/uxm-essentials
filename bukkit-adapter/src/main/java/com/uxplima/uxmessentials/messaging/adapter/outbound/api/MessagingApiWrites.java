package com.uxplima.uxmessentials.messaging.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.application.SendMail;
import com.uxplima.uxmessentials.messaging.application.SendMessage;
import org.jspecify.annotations.NullMarked;

/**
 * The two messaging use cases the published API runs.
 *
 * <p>The very instances behind {@code /msg} and {@code /mail send}, so a message a plugin sends is spied on,
 * ignored, replied to and stored exactly as a typed one is.
 *
 * @param message {@code /msg}
 * @param mail {@code /mail send}
 */
@NullMarked
public record MessagingApiWrites(SendMessage message, SendMail mail) {

    public MessagingApiWrites {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(mail, "mail");
    }

    /** The two as the module built them. */
    public static MessagingApiWrites of(MessagingServices services) {
        Objects.requireNonNull(services, "services");
        return new MessagingApiWrites(services.sendMessage(), services.sendMail());
    }
}
