package com.uxplima.uxmessentials.shared.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.LocalizedCommandVisibilityListener;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class LocalizedCommandVisibilityListenerTest {

    @Test
    void sendsOnlyThePlayersLocalizedAliasesWhileKeepingCanonicalSurface() {
        Player player = player(Locale.forLanguageTag("tr-TR"));
        LocalizedCommandVisibilityListener listener = listener(Optional.empty());
        PlayerCommandSendEvent event = new PlayerCommandSendEvent(
                player,
                new ArrayList<>(List.of(
                        "home", "h", "ev", "doğma", "zuhause", "uxmessentials:zuhause", "other:zuhause", "external")));

        listener.onCommandSend(event);

        assertThat(event.getCommands())
                .containsExactlyInAnyOrder("home", "h", "ev", "doğma", "other:zuhause", "external");
    }

    @Test
    void persistedLangOverrideWinsOverTheClientLocale() {
        Player player = player(Locale.GERMAN);
        LocalizedCommandVisibilityListener listener = listener(Optional.of(Locale.forLanguageTag("tr")));
        PlayerCommandSendEvent event =
                new PlayerCommandSendEvent(player, new ArrayList<>(List.of("home", "ev", "zuhause")));

        listener.onCommandSend(event);

        assertThat(event.getCommands()).containsExactlyInAnyOrder("home", "ev");
    }

    @Test
    void languageWideAliasMatchesRegionalClientsAndExactAliasStaysRegional() {
        List<EffectiveCommand> commands = List.of(new EffectiveCommand(
                new CommandId("home"),
                "home",
                List.of(),
                Map.of("tr", List.of("ev"), "tr-tr", List.of("yuvam")),
                true,
                true));
        LocaleStore noOverride = emptyStore();
        LocalizedCommandVisibilityListener listener =
                new LocalizedCommandVisibilityListener(commands, noOverride, Locale.ENGLISH, "uxmessentials");

        PlayerCommandSendEvent turkey = new PlayerCommandSendEvent(
                player(Locale.forLanguageTag("tr-TR")), new ArrayList<>(List.of("home", "ev", "yuvam")));
        listener.onCommandSend(turkey);
        assertThat(turkey.getCommands()).containsExactlyInAnyOrder("home", "ev", "yuvam");

        PlayerCommandSendEvent cyprus = new PlayerCommandSendEvent(
                player(Locale.forLanguageTag("tr-CY")), new ArrayList<>(List.of("home", "ev", "yuvam")));
        listener.onCommandSend(cyprus);
        assertThat(cyprus.getCommands()).containsExactlyInAnyOrder("home", "ev");
    }

    @Test
    void clientLocaleChangeRefreshesCommandsOnlyWithoutAnOverride() {
        Player automatic = player(Locale.ENGLISH);
        listener(Optional.empty()).onLocaleChange(new PlayerLocaleChangeEvent(automatic, "tr_TR"));
        verify(automatic).updateCommands();

        Player overridden = player(Locale.ENGLISH);
        listener(Optional.of(Locale.GERMAN)).onLocaleChange(new PlayerLocaleChangeEvent(overridden, "tr_TR"));
        verify(overridden, never()).updateCommands();
    }

    @Test
    void brigadierExecutesUnicodeRootLiteralsDirectly() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("doğma").executes(context -> 7));

        assertThat(dispatcher.execute("doğma", new Object())).isEqualTo(7);
    }

    private static LocalizedCommandVisibilityListener listener(Optional<Locale> override) {
        List<EffectiveCommand> commands = List.of(new EffectiveCommand(
                new CommandId("home"),
                "home",
                List.of("h"),
                Map.of("tr", List.of("ev", "doğma"), "de", List.of("zuhause")),
                true,
                true));
        LocaleStore store = new LocaleStore() {
            @Override
            public Optional<Locale> override(PlayerRef player) {
                return override;
            }

            @Override
            public void setOverride(PlayerRef player, Locale locale) {}

            @Override
            public void clearOverride(PlayerRef player) {}
        };
        return new LocalizedCommandVisibilityListener(commands, store, Locale.ENGLISH, "uxmessentials");
    }

    private static LocaleStore emptyStore() {
        return new LocaleStore() {
            @Override
            public Optional<Locale> override(PlayerRef player) {
                return Optional.empty();
            }

            @Override
            public void setOverride(PlayerRef player, Locale locale) {}

            @Override
            public void clearOverride(PlayerRef player) {}
        };
    }

    private static Player player(Locale locale) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Player");
        when(player.locale()).thenReturn(locale);
        return player;
    }
}
