package com.uxplima.uxmessentials.messaging.adapter;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * Where a private message to a named player goes, for {@code /msg} and for a menu's whisper action alike, so both
 * apply the same rules: an online target takes the live delivery (mute, self, toggle, ignore, socialspy, the reply
 * capture), a target online on another backend is relayed there and judged by that backend, and an offline target
 * takes the offline path, which leaves mail when {@code offline-to-mail} is on.
 *
 * <p>An online target the sender cannot see because of vanish is routed through the offline path, so the sender's
 * answer is the same as for a player who is really offline: a hidden player's presence is never given away and they
 * never receive a live delivery. Only a name that resolves to no online player and no played-before profile is
 * answered as unknown.
 */
@NullMarked
public final class PrivateMessageRoute {

    private final MessagingServices services;
    private final Messages messages;
    private final MessageSink sink;

    public PrivateMessageRoute(MessagingServices services, Messages messages, MessageSink sink) {
        this.services = Objects.requireNonNull(services, "services");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Send {@code text} from {@code from} to the player named {@code name}, wherever on the network they are. */
    public void send(PlayerRef from, String name, MessageBody text) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(text, "text");
        Optional<PlayerRef> online = services.players().findOnlineByName(name);
        if (online.isPresent()) {
            PlayerRef target = online.get();
            if (services.vanish().isHiddenFrom(from, target)) {
                services.sendMessage().send(from, target, text, false);
                return;
            }
            services.sendMessage().send(from, target, text);
            return;
        }
        Optional<OnlinePlayer> elsewhere = services.relay().elsewhere(name);
        if (elsewhere.isPresent()) {
            // That backend applies the target's switches.
            services.relay().send(from, elsewhere.get(), text);
            return;
        }
        NameMatch match = services.players().matchByName(name);
        Optional<PlayerRef> offline = match.player();
        if (offline.isEmpty()) {
            MessageLine miss = match.miss(SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", name));
            sink.deliver(from, messages.resolve(from, miss.key(), miss.args()));
            return;
        }
        services.sendMessage().send(from, offline.get(), text, false);
    }
}
