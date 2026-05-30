package com.uxplima.uxmessentials.shared.adapter.outbound.sink;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link MessageSink} implementation: parse an already-resolved MiniMessage source string into a
 * {@code Component} exactly once, then deliver it to the viewer on the viewer's region thread via the
 * injected {@link Scheduler}. The shared {@code <prefix>} tag (which catalog templates reference but
 * never inline) is supplied here as a parsed resolver, so a missing or offline viewer is a silent
 * no-op (the entity scheduler refuses a despawned entity).
 *
 * <p>A single {@link MiniMessage} instance handles parsing; production does not split trusted /
 * untrusted at this layer (docs/03-paper-api §4.1) — all templates are operator-owned catalog
 * content.
 */
@NullMarked
public final class BukkitMessageSink implements MessageSink {

    private final Scheduler scheduler;
    private final MiniMessage miniMessage;
    private final String prefixTemplate;

    public BukkitMessageSink(Scheduler scheduler, String prefixTemplate) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.prefixTemplate = Objects.requireNonNull(prefixTemplate, "prefixTemplate");
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public void deliver(PlayerRef viewer, String renderedText) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(renderedText, "renderedText");
        TagResolver prefix = Placeholder.parsed("prefix", prefixTemplate);
        Component component = miniMessage.deserialize(renderedText, prefix);
        scheduler.onEntity(viewer, () -> sendTo(viewer, component));
    }

    private void sendTo(PlayerRef viewer, Component component) {
        Player player = Bukkit.getPlayer(viewer.uuid());
        if (player != null) {
            player.sendMessage(component);
        }
    }
}
