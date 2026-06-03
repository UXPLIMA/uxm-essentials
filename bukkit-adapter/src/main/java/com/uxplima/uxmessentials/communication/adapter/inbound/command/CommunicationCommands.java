package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.communication.adapter.ChatLock;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitInfoSender;
import com.uxplima.uxmessentials.communication.application.BroadcastOptOut;
import com.uxplima.uxmessentials.communication.application.CommunicationNotifier;
import com.uxplima.uxmessentials.communication.application.InfoRegistry;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the communication context's Brigadier command surface. The static half is the plugin's own
 * {@code /broadcast} (an operator one-off announcement) and {@code /broadcasttoggle} (a per-player opt-out); the
 * dynamic half is one {@link InfoPageCommand} per {@link InfoPage} in the config-derived {@link InfoRegistry}
 * ({@code /rules}, {@code /motd}, {@code /info}, any custom page). The static literals are greppable so the
 * permissions reference can check them; the dynamic literals are operator data, each guarded by the fixed
 * {@code uxmessentials.communication.info.<name>} node shape.
 */
@NullMarked
public final class CommunicationCommands {

    // Prefix prepended to a manual /broadcast body so it reads like an announcement, not plain chat; this is
    // operator-facing MiniMessage content, not a parity-checked MessageKey.
    private static final String BROADCAST_PREFIX = "<gray>[</gray><gold>Broadcast</gold><gray>]</gray> <white>";

    private CommunicationCommands() {}

    /**
     * Every communication command: the static {@code /broadcast} and {@code /broadcasttoggle} plus one per
     * configured info page.
     */
    public static List<CommandRegistration> all(
            BroadcastOptOut optOut,
            InfoRegistry registry,
            BukkitInfoSender infoSender,
            CommunicationNotifier notifier,
            Messages messages,
            BukkitAnnouncerBroadcaster broadcaster,
            MessageSink sink,
            ChatLock chatLock) {
        Objects.requireNonNull(optOut, "optOut");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(infoSender, "infoSender");
        Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(broadcaster, "broadcaster");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(chatLock, "chatLock");
        List<CommandRegistration> commands = new ArrayList<>();
        commands.add(new BroadcastCommand(broadcaster, BROADCAST_PREFIX));
        commands.add(new BroadcastToggleCommand(optOut, messages));
        commands.add(new MeCommand(messages, notifier));
        commands.add(new ClearChatCommand(messages, notifier, sink));
        commands.add(new ToggleChatCommand(chatLock, notifier, messages));
        for (InfoPage page : registry.all()) {
            commands.add(new InfoPageCommand(page.command(), registry, infoSender, notifier, messages));
        }
        return List.copyOf(commands);
    }
}
