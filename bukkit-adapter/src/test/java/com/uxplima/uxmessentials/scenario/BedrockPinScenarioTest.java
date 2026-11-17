package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.bukkit.GameMode;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import org.geysermc.geyser.api.GeyserApi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A Bedrock player proving, creating and re-proving a PIN. The keypad window redraws itself after every tap, which a
 * Bedrock form cannot do, so a Bedrock player types the whole entry into one prompt instead.
 *
 * <p>The server runs Geyser without Floodgate, so there is no form screen and the prompt is the operator's mode for the
 * input point, chat here. That is also the hardest case: whatever the text-input seam falls back to has to carry the
 * player all the way through, and a chat line is the one a test can type.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BedrockPinScenarioTest {

    private static final String TEXT_INPUT = """
            default-mode = anvil
            cancel-keywords = ["cancel"]
            modes {
              "security.verify-pin" = chat
              "security.create-pin" = chat
            }
            """;

    private static final String PIN = "482916";

    private static final String VERIFY_PROMPT = "type your pin or authenticator code";
    private static final String CREATE_PROMPT = "type a new pin";
    private static final String CONFIRM_PROMPT = "type the same pin again";

    /** The small capitals the catalog writes, in alphabet order, so a letter's index is its distance from 'a'. */
    private static final String SMALL_CAPITALS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍ" + "ɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private static final String BACKOFF = "event=security_pin_prompt_backoff";

    private RunningPlugin running;

    @BeforeAll
    void start() {
        GeyserApi.reset();
        running = RunningPlugin.start(Map.of("text-input.conf", TEXT_INPUT));
        MockBukkit.createMockPlugin("Geyser-Spigot");
    }

    @AfterAll
    void stop() {
        running.close();
        GeyserApi.reset();
    }

    @Test
    void aBedrockPlayerHeldAtJoinVerificationTypesTheirPinAndIsLetGo() {
        ScenarioPlayer player = bedrockPlayerWithPin("Bea");

        running.console("security force Bea");

        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CRAFTING);
        assertThat(said(player)).anyMatch(line -> line.contains(VERIFY_PROMPT));
        running.chat(player, PIN);
        assertThat(said(player)).anyMatch(line -> line.contains("verified. welcome back."));
        running.run(player, "pin");
        assertThat(said(player)).anyMatch(line -> line.contains("a pin is set on your account."));
        assertThat(otherProblems()).isEmpty();
    }

    @Test
    void aBedrockPlayerWhoDismissesThePromptWhileHeldIsAskedAgain() {
        ScenarioPlayer player = bedrockPlayerWithPin("Bram");
        running.console("security force Bram");
        RunningPlugin.said(player);

        running.chat(player, "cancel");

        assertThat(said(player)).anyMatch(line -> line.contains(VERIFY_PROMPT));
        running.chat(player, PIN);
        assertThat(said(player)).anyMatch(line -> line.contains("verified. welcome back."));
        assertThat(otherProblems()).isEmpty();
    }

    @Test
    void aWrongTypedPinIsAskedForAgainAndCountsTowardTheSameLockout() {
        ScenarioPlayer player = bedrockPlayerWithPin("Bodhi");
        running.console("security force Bodhi");
        RunningPlugin.said(player);

        running.chat(player, "111111");

        List<String> said = said(player);
        assertThat(said).anyMatch(line -> line.contains("attempts left: 2"));
        assertThat(said).anyMatch(line -> line.contains(VERIFY_PROMPT));
        running.chat(player, "111111");
        running.chat(player, "111111");
        assertThat(player.isOnline()).isFalse();
    }

    @Test
    void aBedrockPlayerTheServerRequiresAPinFromCreatesOneByTypingItTwice() {
        ScenarioPlayer player = running.join("Cleo", joining -> {
            GeyserApi.connect(joining.getUniqueId());
            joining.addAttachment(running.plugin, "uxmessentials.security.pin.required", true);
        });

        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CRAFTING);
        assertThat(said(player)).anyMatch(line -> line.contains(CREATE_PROMPT));
        running.chat(player, PIN);
        assertThat(said(player)).anyMatch(line -> line.contains(CONFIRM_PROMPT));
        running.chat(player, "cancel");
        assertThat(said(player)).anyMatch(line -> line.contains(CONFIRM_PROMPT));
        running.chat(player, PIN);
        assertThat(said(player)).anyMatch(line -> line.contains("pin set."));
        running.run(player, "pin");
        assertThat(said(player)).anyMatch(line -> line.contains("a pin is set on your account."));
        assertThat(otherProblems()).isEmpty();
    }

    @Test
    void aBedrockPlayerTypesTheirPinToRunAProtectedCommand() {
        ScenarioPlayer player = bedrockPlayerWithPin("Rhea");
        player.addAttachment(running.plugin, "uxmessentials.gamemode.use", true);
        player.addAttachment(running.plugin, "uxmessentials.bypass.security", false);
        player.setGameMode(GameMode.SURVIVAL);

        typeCommand(player, "/gamemode creative");

        assertThat(player.getGameMode()).isEqualTo(GameMode.SURVIVAL);
        assertThat(said(player)).anyMatch(line -> line.contains(VERIFY_PROMPT));
        running.chat(player, "111111");
        assertThat(said(player)).anyMatch(line -> line.contains(VERIFY_PROMPT));
        running.chat(player, PIN);
        assertThat(player.getGameMode()).isEqualTo(GameMode.CREATIVE);
        assertThat(otherProblems()).isEmpty();
    }

    @Test
    void aPromptDismissedTheMomentItOpensIsAskedAgainLessAndLessOften() {
        ScenarioPlayer player = bedrockPlayerWithPin("Basil");
        running.console("security force Basil");
        RunningPlugin.said(player);

        running.chat(player, "cancel");
        assertThat(awaitPrompt(player, VERIFY_PROMPT)).isTrue();
        running.chat(player, "cancel");
        assertThat(awaitPrompt(player, VERIFY_PROMPT)).isTrue();
        running.chat(player, "cancel");

        assertThat(said(player)).noneMatch(line -> line.contains(VERIFY_PROMPT));
        assertThat(awaitPrompt(player, VERIFY_PROMPT)).isTrue();
        running.chat(player, PIN);
        assertThat(said(player)).anyMatch(line -> line.contains("verified. welcome back."));
        assertThat(running.problems()).anyMatch(line -> line.contains(BACKOFF));
        assertThat(otherProblems()).isEmpty();
    }

    @Test
    void aStillOpenReauthPromptAnsweredAfterAForcedReverifyLiftsTheFreeze() {
        ScenarioPlayer player = bedrockPlayerWithPin("Rory");
        player.addAttachment(running.plugin, "uxmessentials.gamemode.use", true);
        player.addAttachment(running.plugin, "uxmessentials.bypass.security", false);
        typeCommand(player, "/gamemode creative");
        said(player);

        running.console("security force Rory");
        running.chat(player, PIN);

        assertThat(said(player)).anyMatch(line -> line.contains("verified. welcome back."));
        running.run(player, "pin");
        assertThat(said(player)).anyMatch(line -> line.contains("a pin is set on your account."));
    }

    @Test
    void aJavaPlayerStillTapsTheKeypad() {
        ScenarioPlayer player = running.join("Jade");
        running.run(player, "pin set " + PIN);

        running.console("security force Jade");

        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CHEST);
        assertThat(said(player)).noneMatch(line -> line.contains(VERIFY_PROMPT));
    }

    private ScenarioPlayer bedrockPlayerWithPin(String name) {
        ScenarioPlayer player = running.join(name, joining -> GeyserApi.connect(joining.getUniqueId()));
        running.run(player, "pin set " + PIN);
        RunningPlugin.said(player);
        return player;
    }

    /**
     * What the player was told since the last call, folded to plain lower-case letters: the shipped catalog writes
     * its lines in small capitals, which a test would otherwise have to spell the same way.
     */
    private static List<String> said(ScenarioPlayer player) {
        return RunningPlugin.said(player).stream()
                .map(BedrockPinScenarioTest::fold)
                .toList();
    }

    /**
     * What the plugin warned about, less the one warning a prompt dismissed again and again rightly raises: the plugin
     * runs once for the whole class, so that warning stays in the log for every test after the one that causes it.
     */
    private List<String> otherProblems() {
        return running.problems().stream()
                .filter(line -> !line.contains(BACKOFF))
                .toList();
    }

    /** Lets the server run until the player is asked {@code prompt}, for at most ten game seconds. */
    private boolean awaitPrompt(ScenarioPlayer player, String prompt) {
        for (int second = 0; second < 10; second++) {
            if (said(player).stream().anyMatch(line -> line.contains(prompt))) {
                return true;
            }
            running.settle();
        }
        return false;
    }

    private static String fold(String line) {
        StringBuilder folded = new StringBuilder(line.length());
        line.codePoints().forEach(point -> {
            int index = SMALL_CAPITALS.indexOf(point);
            folded.append(index < 0 ? Character.toString(point) : Character.toString('a' + index));
        });
        return folded.toString();
    }

    /** A command typed into chat: the server raises the preprocess event first and runs only what it lets through. */
    private void typeCommand(ScenarioPlayer player, String line) {
        PlayerCommandPreprocessEvent typed = new PlayerCommandPreprocessEvent(player, line);
        running.server.getPluginManager().callEvent(typed);
        if (!typed.isCancelled()) {
            player.performCommand(typed.getMessage().substring(1));
        }
        running.settle();
    }
}
