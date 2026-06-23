package com.uxplima.uxmessentials.playerstate.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.ExecutionException;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.command.PersonalTimeCommand;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.command.PersonalWeatherCommand;
import com.uxplima.uxmessentials.playerstate.application.Burn;
import com.uxplima.uxmessentials.playerstate.application.ClearInventory;
import com.uxplima.uxmessentials.playerstate.application.Extinguish;
import com.uxplima.uxmessentials.playerstate.application.Feed;
import com.uxplima.uxmessentials.playerstate.application.Freeze;
import com.uxplima.uxmessentials.playerstate.application.Heal;
import com.uxplima.uxmessentials.playerstate.application.ListNearby;
import com.uxplima.uxmessentials.playerstate.application.OpenContainer;
import com.uxplima.uxmessentials.playerstate.application.ResetPlaytime;
import com.uxplima.uxmessentials.playerstate.application.ResetRest;
import com.uxplima.uxmessentials.playerstate.application.SetAir;
import com.uxplima.uxmessentials.playerstate.application.SetExperience;
import com.uxplima.uxmessentials.playerstate.application.SetFoodLevel;
import com.uxplima.uxmessentials.playerstate.application.SetGamemode;
import com.uxplima.uxmessentials.playerstate.application.SetHealth;
import com.uxplima.uxmessentials.playerstate.application.SetPersonalTime;
import com.uxplima.uxmessentials.playerstate.application.SetPersonalWeather;
import com.uxplima.uxmessentials.playerstate.application.SetSpeed;
import com.uxplima.uxmessentials.playerstate.application.ShowPing;
import com.uxplima.uxmessentials.playerstate.application.ShowPlaytime;
import com.uxplima.uxmessentials.playerstate.application.ShowPosition;
import com.uxplima.uxmessentials.playerstate.application.Suicide;
import com.uxplima.uxmessentials.playerstate.application.ToggleClearInventoryConfirm;
import com.uxplima.uxmessentials.playerstate.application.ToggleFly;
import com.uxplima.uxmessentials.playerstate.application.ToggleGlow;
import com.uxplima.uxmessentials.playerstate.application.ToggleGod;
import com.uxplima.uxmessentials.playerstate.application.ToggleNightVision;
import com.uxplima.uxmessentials.playerstate.domain.PersonalTime;
import com.uxplima.uxmessentials.playerstate.domain.PersonalWeather;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Item 13 verification: {@code /ptime reset} and {@code /pweather reset} both return the player to server
 * time/weather and both offer {@code reset} in tab-completion so the return-to-server form is discoverable. The
 * use cases are mocked so the test asserts the parsed value the adapter applies (a reset {@link PersonalTime} /
 * {@link PersonalWeather#RESET}) and reads the live suggestion list Brigadier builds while the player types.
 */
class PersonalTimeWeatherResetTest {

    private static final String PTIME = "uxmessentials.ptime.use";
    private static final String PWEATHER = "uxmessentials.pweather.use";

    private ServerMock server;
    private SetPersonalTime personalTime;
    private SetPersonalWeather personalWeather;
    private PersonalTimeCommand ptime;
    private PersonalWeatherCommand pweather;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        personalTime = mock(SetPersonalTime.class);
        personalWeather = mock(SetPersonalWeather.class);
        PlayerStateServices services = services();
        ptime = new PersonalTimeCommand(services, new EchoMessages());
        pweather = new PersonalWeatherCommand(services, new EchoMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void ptimeResetParsesAndAppliesAReset() {
        PlayerMock player = server.addPlayer("Player");
        player.addAttachment(MockBukkit.createMockPlugin(), PTIME, true);

        execute(ptime.build(), CommandSourceStackMock.from(player), "ptime reset");

        verify(personalTime).apply(any(PlayerRef.class), eq(PersonalTime.resetTime()));
    }

    @Test
    void pweatherResetParsesAndAppliesAReset() {
        PlayerMock player = server.addPlayer("Player");
        player.addAttachment(MockBukkit.createMockPlugin(), PWEATHER, true);

        execute(pweather.build(), CommandSourceStackMock.from(player), "pweather reset");

        verify(personalWeather).apply(any(PlayerRef.class), eq(PersonalWeather.RESET));
    }

    @Test
    void ptimeOffersResetInTabCompletion() {
        PlayerMock player = server.addPlayer("Player");
        player.addAttachment(MockBukkit.createMockPlugin(), PTIME, true);

        assertThat(suggestions(ptime.build(), CommandSourceStackMock.from(player), "ptime "))
                .contains("reset");
    }

    @Test
    void pweatherOffersResetInTabCompletion() {
        PlayerMock player = server.addPlayer("Player");
        player.addAttachment(MockBukkit.createMockPlugin(), PWEATHER, true);

        assertThat(suggestions(pweather.build(), CommandSourceStackMock.from(player), "pweather "))
                .contains("reset");
    }

    private void execute(LiteralCommandNode<CommandSourceStack> node, CommandSourceStack source, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        try {
            dispatcher.execute(input, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private List<String> suggestions(
            LiteralCommandNode<CommandSourceStack> node, CommandSourceStack source, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        try {
            return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).get().getList().stream()
                    .map(Suggestion::getText)
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new AssertionError("suggestion build failed for: " + input, e);
        }
    }

    private PlayerStateServices services() {
        return new PlayerStateServices(
                mock(ToggleGod.class),
                mock(ToggleFly.class),
                mock(Heal.class),
                mock(Feed.class),
                mock(SetFoodLevel.class),
                mock(SetHealth.class),
                mock(SetGamemode.class),
                mock(SetSpeed.class),
                mock(Extinguish.class),
                mock(ClearInventory.class),
                mock(ToggleClearInventoryConfirm.class),
                mock(OpenContainer.class),
                mock(Suicide.class),
                mock(ListNearby.class),
                mock(ToggleNightVision.class),
                mock(ToggleGlow.class),
                personalTime,
                personalWeather,
                mock(SetExperience.class),
                mock(SetAir.class),
                mock(Burn.class),
                mock(Freeze.class),
                mock(ShowPosition.class),
                mock(ShowPing.class),
                mock(ShowPlaytime.class),
                mock(ResetPlaytime.class),
                mock(ResetRest.class),
                mock(PlayerLookup.class));
    }

    /** Echoes the full catalog key as one line; the reset/apply assertions read the verified use-case call. */
    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, java.util.Map<String, String> placeholders) {
            return key.key();
        }
    }
}
