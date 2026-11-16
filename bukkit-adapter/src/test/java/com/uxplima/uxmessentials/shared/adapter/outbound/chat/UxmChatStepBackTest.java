package com.uxplima.uxmessentials.shared.adapter.outbound.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import com.uxplima.chat.api.UxmChat;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class UxmChatStepBackTest {

    private ServerMock server;

    @BeforeEach
    void mock() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void unmock() {
        MockBukkit.unmock();
    }

    /** uxmChat installed or not, enabled or not, running these features, on a server that has run this many ticks. */
    private UxmChatStepBack stepBack(boolean installed, boolean enabled, int tick, @Nullable Set<String> features) {
        if (installed) {
            Plugin chat = MockBukkit.createMockPlugin("uxmChat");
            if (features != null) {
                server.getServicesManager()
                        .register(UxmChat.class, new UxmChat(features), chat, ServicePriority.Normal);
            }
            if (!enabled) {
                server.getPluginManager().disablePlugin(chat);
            }
        }
        server.getScheduler().performTicks(tick);
        return new UxmChatStepBack(server);
    }

    @Test
    void withoutUxmChatNothingIsLeftToIt() {
        UxmChatStepBack stepBack = stepBack(false, false, 100, null);

        assertThat(stepBack.coversCommand("msg")).isFalse();
        assertThat(stepBack.covers(UxmChatStepBack.JOIN_QUIT)).isFalse();
    }

    @Test
    void aFeatureUxmChatRunsIsLeftToItAndOneItTurnedOffStaysHere() {
        UxmChatStepBack stepBack =
                stepBack(true, true, 100, Set.of(UxmChatStepBack.PRIVATE_MESSAGES, UxmChatStepBack.CHAT_FORMAT));

        assertThat(stepBack.coversCommand("msg")).isTrue();
        assertThat(stepBack.coversCommand("ignore"))
                .as("ignore is its own feature")
                .isFalse();
        assertThat(stepBack.coversCommand("mail")).isFalse();
        assertThat(stepBack.covers(UxmChatStepBack.CHAT_FORMAT)).isTrue();
        assertThat(stepBack.coversCommand("home")).isFalse();
    }

    @Test
    void whileUxmChatHasNotStartedYetItsChatLinesCountAsIts() {
        assertThat(stepBack(true, false, 0, null).covers(UxmChatStepBack.JOIN_QUIT))
                .isTrue();
    }

    @Test
    void onceTheServerTicksAUxmChatThatNeverStartedTakesNoChatLines() {
        assertThat(stepBack(true, false, 40, null).covers(UxmChatStepBack.JOIN_QUIT))
                .isFalse();
    }

    @Test
    void aUxmChatThatFailedToStartTakesNoCommandWithIt() {
        // Commands are decided after every plugin has enabled, still at tick 0.
        assertThat(stepBack(true, false, 0, null).coversCommand("mail")).isFalse();
    }
}
