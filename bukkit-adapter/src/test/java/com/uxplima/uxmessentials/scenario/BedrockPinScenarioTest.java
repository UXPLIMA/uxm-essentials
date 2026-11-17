package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.bukkit.GameMode;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.geysermc.geyser.api.GeyserApi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Players proving, creating and re-proving a PIN. The keypad window redraws itself after every tap, which a Bedrock
 * form cannot do, so a Bedrock player types the whole entry into one prompt instead; a Java player keeps the keypad.
 *
 * <p>The server runs Geyser without Floodgate, so there is no form screen and the prompt is the operator's mode for the
 * input point. Anvil is the default and the one a Geyser-only server ships with, so that is what these players type
 * into, while the verify point is set to chat: chat is refused for a PIN, so it must open as an anvil all the same.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("deprecation") // the legacy chat event is the one a chat logger may still listen to
class BedrockPinScenarioTest {

    private static final String TEXT_INPUT = """
            default-mode = anvil
            cancel-keywords = ["cancel"]
            modes {
              "security.verify-pin" = chat
              "security.create-pin" = anvil
            }
            """;

    private static final String PIN = "482916";
    private static final String OTHER_PIN = "135790";

    private static final String CREATE_PROMPT = "type a new pin";

    /** The small capitals the catalog writes, in alphabet order, so a letter's index is its distance from 'a'. */
    private static final String SMALL_CAPITALS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍ" + "ɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    /** The warnings these scenarios rightly raise, each once for the whole class. */
    private static final String BACKOFF = "event=security_pin_prompt_backoff";

    private static final String CHAT_REFUSED = "event=security_input_chat_refused";

    /** Where the keypad puts each digit, 0 to 9, and its submit button. */
    private static final int[] DIGIT_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    private static final int SUBMIT_SLOT = 33;

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
    void aBedrockPlayerHeldAtJoinVerificationTypesTheirPinIntoAnAnvilAndIsLetGo() {
        ScenarioPlayer player = bedrockPlayerWithPin("Bea");

        running.console("security force Bea");

        assertThat(asked(player)).isTrue();
        type(player, PIN);
        assertThat(said(player)).anyMatch(line -> line.contains("verified. welcome back."));
        running.run(player, "pin");
        assertThat(said(player)).anyMatch(line -> line.contains("a pin is set on your account."));
        assertThat(running.problems()).anyMatch(line -> line.contains(CHAT_REFUSED));
        assertThat(otherProblems()).isEmpty();
    }

    @Test
    void aBedrockPlayerWhoDismissesThePromptWhileHeldIsAskedAgain() {
        ScenarioPlayer player = bedrockPlayerWithPin("Bram");
        running.console("security force Bram");

        dismiss(player);

        assertThat(awaitAsked(player)).isTrue();
        type(player, PIN);
        assertThat(said(player)).anyMatch(line -> line.contains("verified. welcome back."));
        assertThat(otherProblems()).isEmpty();
    }

    @Test
    void aWrongTypedPinIsAskedForAgainAndCountsTowardTheSameLockout() {
        ScenarioPlayer player = bedrockPlayerWithPin("Bodhi");
        running.console("security force Bodhi");
        said(player);

        type(player, OTHER_PIN);

        assertThat(said(player)).anyMatch(line -> line.contains("attempts left: 2"));
        assertThat(awaitAsked(player)).isTrue();
        type(player, OTHER_PIN);
        assertThat(awaitAsked(player)).isTrue();
        type(player, OTHER_PIN);
        assertThat(player.isOnline()).isFalse();
    }

    @Test
    void aBedrockPlayerTheServerRequiresAPinFromCreatesOneByTypingItTwice() {
        ScenarioPlayer player = running.join("Cleo", joining -> {
            GeyserApi.connect(joining.getUniqueId());
            joining.addAttachment(running.plugin, "uxmessentials.security.pin.required", true);
        });

        assertThat(asked(player)).isTrue();
        type(player, PIN);
        assertThat(awaitAsked(player)).isTrue();
        dismiss(player);
        assertThat(awaitAsked(player)).isTrue();
        type(player, PIN);
        assertThat(said(player)).anyMatch(line -> line.contains("pin set."));
        running.run(player, "pin");
        assertThat(said(player)).anyMatch(line -> line.contains("a pin is set on your account."));
        assertThat(otherProblems()).isEmpty();
    }

    @Test
    void digitsFromAnotherScriptNeitherSlipPastTheBlockedListNorMakeAPinTheKeypadCannotEnter() {
        ScenarioPlayer player = running.join("Faye", joining -> {
            GeyserApi.connect(joining.getUniqueId());
            joining.addAttachment(running.plugin, "uxmessentials.security.pin.required", true);
        });
        said(player);

        type(player, "１２３４５６");
        assertThat(said(player)).anyMatch(line -> line.contains("that pin is too easy to guess."));
        assertThat(awaitAsked(player)).isTrue();
        type(player, "٤٨٢٩١٦");
        assertThat(said(player)).anyMatch(line -> line.contains("your pin must be digits only."));
        assertThat(awaitAsked(player)).isTrue();
        type(player, "４８２９１６");
        assertThat(awaitAsked(player)).isTrue();
        type(player, PIN);

        assertThat(said(player)).anyMatch(line -> line.contains("pin set."));
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
        assertThat(asked(player)).isTrue();
        type(player, OTHER_PIN);
        assertThat(awaitAsked(player)).isTrue();
        type(player, PIN);
        assertThat(player.getGameMode()).isEqualTo(GameMode.CREATIVE);
        assertThat(otherProblems()).isEmpty();
    }

    @Test
    void aPromptDismissedTheMomentItOpensIsAskedAgainLessAndLessOften() {
        ScenarioPlayer player = bedrockPlayerWithPin("Basil");
        running.console("security force Basil");

        dismiss(player);
        assertThat(awaitAsked(player)).isTrue();
        dismiss(player);
        assertThat(awaitAsked(player)).isTrue();
        dismiss(player);

        assertThat(asked(player)).isFalse();
        assertThat(awaitAsked(player)).isTrue();
        type(player, PIN);
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

        running.console("security force Rory");
        type(player, PIN);

        assertThat(said(player)).anyMatch(line -> line.contains("verified. welcome back."));
        running.run(player, "pin");
        assertThat(said(player)).anyMatch(line -> line.contains("a pin is set on your account."));
    }

    @Test
    void aBedrockPromptStillUpWhenAResetSendsThePlayerToCreateAPinIsReplacedByTheRightQuestion() {
        ScenarioPlayer player = bedrockPlayerWithPin("Reese");
        running.console("security force Reese");
        assertThat(asked(player)).isTrue();
        player.addAttachment(running.plugin, "uxmessentials.security.pin.required", true);

        running.console("security reset Reese all");

        assertThat(asked(player)).isTrue();
        assertThat(question(player)).contains(CREATE_PROMPT);
        type(player, OTHER_PIN);
        assertThat(awaitAsked(player)).isTrue();
        type(player, OTHER_PIN);
        assertThat(said(player)).anyMatch(line -> line.contains("pin set."));
    }

    @Test
    void aBedrockCreateSessionLeftByAQuitCannotTakeTheNextVerificationsEntriesAsANewPin() {
        ScenarioPlayer first = running.join("Stella", joining -> {
            GeyserApi.connect(joining.getUniqueId());
            joining.addAttachment(running.plugin, "uxmessentials.security.pin.required", true);
        });
        assertThat(asked(first)).isTrue();
        running.quit(first);
        ScenarioPlayer player = running.join("Stella");
        running.run(player, "pin set " + PIN);
        running.console("security force Stella");
        said(player);

        type(player, OTHER_PIN);
        assertThat(awaitAsked(player)).isTrue();
        type(player, OTHER_PIN);

        List<String> said = said(player);
        assertThat(said).noneMatch(line -> line.contains("pin set."));
        assertThat(said).anyMatch(line -> line.contains("attempts left: 1"));
        stillHeld(player);
    }

    @Test
    void aJavaCreateSessionLeftByAQuitCannotTakeTheNextVerificationsEntriesAsANewPin() {
        ScenarioPlayer first = running.join(
                "Jules", joining -> joining.addAttachment(running.plugin, "uxmessentials.security.pin.required", true));
        assertThat(first.getOpenInventory().getType()).isEqualTo(InventoryType.CHEST);
        running.quit(first);
        ScenarioPlayer player = running.join("Jules");
        running.run(player, "pin set " + PIN);
        running.console("security force Jules");
        said(player);

        tap(player, OTHER_PIN);
        tap(player, OTHER_PIN);

        List<String> said = said(player);
        assertThat(said).noneMatch(line -> line.contains("pin set."));
        assertThat(said).anyMatch(line -> line.contains("attempts left: 1"));
        stillHeld(player);
    }

    @Test
    void aPlayerWhoNeverCreatesTheRequiredPinIsLetGoByTheEntryTimeout() {
        ScenarioPlayer player = running.join("Tess", joining -> {
            GeyserApi.connect(joining.getUniqueId());
            joining.addAttachment(running.plugin, "uxmessentials.security.pin.required", true);
        });
        assertThat(asked(player)).isTrue();

        // The shipped entry timeout is sixty seconds.
        for (int second = 0; second < 62 && player.isOnline(); second++) {
            running.settle();
        }

        assertThat(player.isOnline()).isFalse();
    }

    @Test
    void nothingAHeldPlayerTypesIntoChatReachesChat() {
        ScenarioPlayer player = bedrockPlayerWithPin("Gale");
        running.console("security force Gale");

        AsyncChatEvent chat = running.chat(player, PIN);
        AsyncPlayerChatEvent legacy =
                new AsyncPlayerChatEvent(true, player, PIN, new HashSet<>(running.server.getOnlinePlayers()));
        running.callOffTick(legacy);

        assertThat(chat.isCancelled()).isTrue();
        assertThat(legacy.isCancelled()).isTrue();
        type(player, PIN);
        assertThat(said(player)).anyMatch(line -> line.contains("verified. welcome back."));
    }

    @Test
    void aJavaPlayerStillTapsTheKeypad() {
        ScenarioPlayer player = running.join("Jade");
        running.run(player, "pin set " + PIN);

        running.console("security force Jade");

        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CHEST);
        tap(player, PIN);
        assertThat(said(player)).anyMatch(line -> line.contains("verified. welcome back."));
    }

    private ScenarioPlayer bedrockPlayerWithPin(String name) {
        ScenarioPlayer player = running.join(name, joining -> GeyserApi.connect(joining.getUniqueId()));
        running.run(player, "pin set " + PIN);
        said(player);
        return player;
    }

    /** Whether the player has a typed prompt in front of them: an anvil, on a Geyser-only server. */
    private static boolean asked(ScenarioPlayer player) {
        return player.getOpenInventory().getType() == InventoryType.ANVIL;
    }

    /** The question on the open anvil: the name of the paper it puts in front of the player. */
    private static String question(ScenarioPlayer player) {
        ItemStack paper = player.getOpenInventory().getTopInventory().getItem(0);
        assertThat(paper).as("the anvil's question").isNotNull();
        Component name = paper.getItemMeta().displayName();
        assertThat(name).as("the anvil's question").isNotNull();
        return fold(PlainTextComponentSerializer.plainText().serialize(name));
    }

    /** Lets the server run until the player is asked again, for at most ten game seconds. */
    private boolean awaitAsked(ScenarioPlayer player) {
        for (int second = 0; second < 10 && !asked(player); second++) {
            running.settle();
        }
        return asked(player);
    }

    /**
     * Types {@code text} into the open anvil and takes the result, as a player does. The anvil's own view type is not
     * in MockBukkit, so the click carries a view that answers the rename text and hands every other question to the
     * window that is really open.
     */
    private void type(ScenarioPlayer player, String text) {
        InventoryView open = player.getOpenInventory();
        assertThat(open.getType()).as("an anvil to type into").isEqualTo(InventoryType.ANVIL);
        AnvilView anvil = (AnvilView) Proxy.newProxyInstance(
                AnvilView.class.getClassLoader(), new Class<?>[] {AnvilView.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getRenameText")) {
                        return text;
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            default -> "typed anvil";
                        };
                    }
                    return InventoryView.class
                            .getMethod(method.getName(), method.getParameterTypes())
                            .invoke(open, args);
                });
        InventoryClickEvent take = new InventoryClickEvent(
                anvil, InventoryType.SlotType.RESULT, 2, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        running.server.getPluginManager().callEvent(take);
        running.settle();
    }

    /** Closes the prompt without answering it. */
    private void dismiss(ScenarioPlayer player) {
        player.closeInventory();
        running.settle();
    }

    /** Taps {@code digits} on the open keypad and submits them. */
    private void tap(ScenarioPlayer player, String digits) {
        assertThat(player.getOpenInventory().getType()).as("a keypad to tap").isEqualTo(InventoryType.CHEST);
        digits.chars().forEach(digit -> running.click(player, DIGIT_SLOTS[digit - '0']));
        running.click(player, SUBMIT_SLOT);
    }

    /** A held player's command is refused with the nudge to verify first. */
    private void stillHeld(ScenarioPlayer player) {
        said(player);
        running.run(player, "pin");
        assertThat(said(player)).anyMatch(line -> line.contains("verify your account first"));
    }

    /**
     * What the plugin warned about, less the warnings these scenarios rightly raise: the plugin runs once for the whole
     * class, so such a warning stays in the log for every test after the one that causes it.
     */
    private List<String> otherProblems() {
        return running.problems().stream()
                .filter(line -> !line.contains(BACKOFF) && !line.contains(CHAT_REFUSED))
                .toList();
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
