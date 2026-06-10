package com.uxplima.uxmessentials.shared.adapter.outbound.update;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Version;
import org.jspecify.annotations.NullMarked;

/**
 * Notifies operators on join when the {@link UpdateChecker} has found a newer release. A non-op, or a join while
 * the checker reports no update, is a silent no-op. The notice is the {@code common.update-available}
 * {@link SharedMessageKey} resolved in the operator's locale and delivered through the {@link MessageSink}, which
 * hops to the joining player's region thread, so no Bukkit entity is touched off-thread.
 *
 * <p>The URL placeholder is the operator's own configured source URL — data they authored — passed as a literal
 * {@code {url}} substitution, never an inline plugin literal. The listener runs at {@code MONITOR} since it only
 * observes the join.
 */
@NullMarked
public final class UpdateNoticeListener implements Listener {

    private final UpdateChecker checker;
    private final String url;
    private final Messages messages;
    private final MessageSink sink;

    public UpdateNoticeListener(UpdateChecker checker, String url, Messages messages, MessageSink sink) {
        this.checker = Objects.requireNonNull(checker, "checker");
        this.url = Objects.requireNonNull(url, "url");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp()) {
            return;
        }
        Optional<Version> latest = checker.available();
        if (latest.isEmpty()) {
            return;
        }
        PlayerRef who = BukkitRefs.toRef(player);
        Map<String, String> placeholders = Map.of(
                "current", checker.current().toString(),
                "latest", latest.get().toString(),
                "url", url);
        sink.deliver(who, messages.resolve(who, SharedMessageKey.UPDATE_AVAILABLE, placeholders));
    }
}
