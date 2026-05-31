package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitInfoSender;
import com.uxplima.uxmessentials.communication.application.BroadcastOptOut;
import com.uxplima.uxmessentials.communication.application.CommunicationNotifier;
import com.uxplima.uxmessentials.communication.application.InfoRegistry;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the communication context's Brigadier command surface. The static half is the plugin's own
 * {@code /broadcasttoggle}; the dynamic half is one {@link InfoPageCommand} per {@link InfoPage} in the
 * config-derived {@link InfoRegistry} ({@code /rules}, {@code /motd}, {@code /info}, any custom page). The static
 * literal is greppable so the permissions reference can check it; the dynamic literals are operator data, each
 * guarded by the fixed {@code uxmessentials.communication.info.<name>} node shape.
 */
@NullMarked
public final class CommunicationCommands {

    private CommunicationCommands() {}

    /** Every communication command: the static {@code /broadcasttoggle} plus one per configured info page. */
    public static List<CommandRegistration> all(
            BroadcastOptOut optOut,
            InfoRegistry registry,
            BukkitInfoSender infoSender,
            CommunicationNotifier notifier,
            Messages messages) {
        Objects.requireNonNull(optOut, "optOut");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(infoSender, "infoSender");
        Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(messages, "messages");
        List<CommandRegistration> commands = new ArrayList<>();
        commands.add(new BroadcastToggleCommand(optOut, messages));
        for (InfoPage page : registry.all()) {
            commands.add(new InfoPageCommand(page.command(), registry, infoSender, notifier, messages));
        }
        return List.copyOf(commands);
    }
}
