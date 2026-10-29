package com.uxplima.uxmessentials.shared.adapter.outbound.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;

import com.uxplima.chat.api.UxmChat;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class UxmChatStepBackTest {

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, java.util.function.Function<String, @Nullable Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Object answer = answers.apply(method.getName());
            if (answer == null && method.getReturnType() == boolean.class) {
                return false;
            }
            return answer;
        });
    }

    /** A server with uxmChat installed or not, enabled or not, running the given features. */
    private static Server server(boolean installed, boolean enabled, int tick, @Nullable Set<String> features) {
        Plugin chat = fake(Plugin.class, name -> name.equals("isEnabled") ? enabled : null);
        PluginManager plugins = fake(PluginManager.class, name -> name.equals("getPlugin") && installed ? chat : null);
        UxmChat service = new UxmChat(features == null ? Set.of() : features);
        ServicesManager services = fake(ServicesManager.class, name -> switch (name) {
            case "getKnownServices" -> features == null ? List.of() : List.of(UxmChat.class);
            case "load" -> features == null ? null : service;
            default -> null;
        });
        return fake(Server.class, name -> switch (name) {
            case "getPluginManager" -> plugins;
            case "getServicesManager" -> services;
            case "getCurrentTick" -> tick;
            default -> null;
        });
    }

    @Test
    void withoutUxmChatNothingIsLeftToIt() {
        UxmChatStepBack stepBack = new UxmChatStepBack(server(false, false, 100, null));

        assertThat(stepBack.coversCommand("msg")).isFalse();
        assertThat(stepBack.covers(UxmChatStepBack.JOIN_QUIT)).isFalse();
    }

    @Test
    void aFeatureUxmChatRunsIsLeftToItAndOneItTurnedOffStaysHere() {
        UxmChatStepBack stepBack = new UxmChatStepBack(
                server(true, true, 100, Set.of(UxmChatStepBack.PRIVATE_MESSAGES, UxmChatStepBack.CHAT_FORMAT)));

        assertThat(stepBack.coversCommand("msg")).isTrue();
        assertThat(stepBack.coversCommand("ignore"))
                .as("ignore is its own feature")
                .isFalse();
        assertThat(stepBack.coversCommand("mail")).isFalse();
        assertThat(stepBack.covers(UxmChatStepBack.CHAT_FORMAT)).isTrue();
        assertThat(stepBack.coversCommand("home")).isFalse();
    }

    @Test
    void whileUxmChatHasNotStartedYetItsChatLinesCountAsItsButOnceTheServerTicksTheyDoNot() {
        assertThat(new UxmChatStepBack(server(true, false, 0, null)).covers(UxmChatStepBack.JOIN_QUIT))
                .isTrue();
        assertThat(new UxmChatStepBack(server(true, false, 40, null)).covers(UxmChatStepBack.JOIN_QUIT))
                .isFalse();
    }

    @Test
    void aUxmChatThatFailedToStartTakesNoCommandWithIt() {
        // Commands are decided after every plugin has enabled, still at tick 0.
        assertThat(new UxmChatStepBack(server(true, false, 0, null)).coversCommand("mail"))
                .isFalse();
    }
}
