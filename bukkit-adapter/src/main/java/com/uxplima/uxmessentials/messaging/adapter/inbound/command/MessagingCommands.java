package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import java.util.List;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.adapter.PrivateMessageRoute;
import com.uxplima.uxmessentials.messaging.adapter.inbound.gui.MessagingGuiViews;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the messaging context's Brigadier command surface (no public chat) as {@link CommandRegistration}s over the constructed {@link MessagingServices}. Collected in one
 * greppable table so the literal/permission pairing matches the permissions reference and its entries in
 * {@code CommandSpecCatalog}; the plugin's {@code LifecycleEvents.COMMANDS} handler registers each.
 */
@NullMarked
public final class MessagingCommands {

    private MessagingCommands() {}

    /** Every messaging command, in surface order. The GUI views back the no-arg branches and {@code /msgsettings}. */
    public static List<CommandRegistration> all(
            MessagingServices services,
            Messages messages,
            MessageSink sink,
            MessagingGuiViews views,
            Supplier<List<PlayerRef>> elsewhere,
            PrivateMessageRoute route) {
        return List.of(
                new MsgCommand(services, messages, sink, route),
                new ReplyCommand(services, messages, sink),
                new MailCommand(services, messages, sink, views, elsewhere),
                new MsgToggleCommand(services, messages, sink),
                new RtoggleCommand(services, messages, sink),
                new IgnoreCommand(services, messages, sink, views),
                new UnignoreCommand(services, messages, sink),
                new IgnoreListCommand(services, messages, sink, views),
                new SocialSpyCommand(services, messages, sink),
                new MailClearCommand(services, messages, sink),
                new HelpOpCommand(services, messages, sink),
                new MsgSettingsCommand(services, messages, sink, views));
    }
}
