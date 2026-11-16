package com.uxplima.uxmessentials.moderation.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.moderation.adapter.inbound.listener.MutedChatListener;
import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Public chat arrives on its own async thread, where the storage lane has read no permission answers. The chat mute
 * check is wired to the live permission reader, so a muted speaker is stopped there and an exempt one is not, and the
 * exempt node is read only for a speaker who is muted.
 */
class MutedChatWiringTest {

    private static final UUID SPEAKER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private final Set<UUID> muted = new HashSet<>();
    private final Set<UUID> exempt = new HashSet<>();
    private final List<String> asked = new ArrayList<>();
    private final List<String> delivered = new ArrayList<>();
    private final MutedChatListener listener = ModerationWiring.mutedChat(
            who -> muted.contains(who.uuid()),
            new LivePermissions(),
            (viewer, key, placeholders) -> key.key(),
            (viewer, text) -> delivered.add(text));

    @Test
    void aMutedSpeakerIsStoppedOnTheChatThread() throws InterruptedException {
        muted.add(SPEAKER);

        AsyncChatEvent event = chatOffTheServerThread();

        assertThat(event.isCancelled()).isTrue();
        assertThat(delivered).hasSize(1);
        assertThat(asked).containsExactly(ModerationGuard.EXEMPT_NODE);
    }

    @Test
    void aMutedExemptSpeakerTalks() throws InterruptedException {
        muted.add(SPEAKER);
        exempt.add(SPEAKER);

        AsyncChatEvent event = chatOffTheServerThread();

        assertThat(event.isCancelled()).isFalse();
        assertThat(delivered).isEmpty();
    }

    @Test
    void aSpeakerWhoIsNotMutedIsNotAskedAboutTheExemptNode() throws InterruptedException {
        AsyncChatEvent event = chatOffTheServerThread();

        assertThat(event.isCancelled()).isFalse();
        assertThat(asked).isEmpty();
    }

    private AsyncChatEvent chatOffTheServerThread() throws InterruptedException {
        AsyncChatEvent event = new AsyncChatEvent(
                true,
                speaker(),
                new HashSet<>(),
                ChatRenderer.defaultRenderer(),
                Component.text("hello"),
                Component.text("hello"),
                SignedMessage.system("hello", Component.text("hello")));
        AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
        Thread chat = new Thread(() -> {
            try {
                listener.onChat(event);
            } catch (RuntimeException thrown) {
                failure.set(thrown);
            }
        });
        chat.start();
        chat.join();
        assertThat(failure.get()).isNull();
        return event;
    }

    private static Player speaker() {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> SPEAKER;
                    case "getName" -> "Speaker";
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    /** The kernel's live reader, as the wiring hands it over: it records every node it is asked about. */
    private final class LivePermissions implements Permissions {

        @Override
        public boolean has(PlayerRef who, String node) {
            asked.add(node);
            return exempt.contains(who.uuid());
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }
}
