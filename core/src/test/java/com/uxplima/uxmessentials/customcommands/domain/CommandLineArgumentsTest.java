package com.uxplima.uxmessentials.customcommands.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CommandLineArgumentsTest {

    private static final CommandArgument TARGET = CommandArgument.of("target", ArgumentKind.PLAYER);
    private static final CommandArgument AMOUNT = CommandArgument.of("amount", ArgumentKind.INT);
    private static final CommandArgument NOTE =
            new CommandArgument("note", ArgumentKind.STRING, false, true, Optional.empty(), Optional.empty());
    private static final CommandArgument OPENED_NOTE =
            new CommandArgument("note", ArgumentKind.STRING, false, true, Optional.empty(), Optional.empty(), true);

    @Test
    void aPlayerNameAndANumberGoIntoAConsoleLine() {
        CustomCommand give = command(List.of(TARGET, AMOUNT), "console: give %arg_target% diamond %arg_amount%");

        assertThat(CommandLineArguments.refused(give, Map.of("target", "Steve", "amount", "3")))
                .isEmpty();
    }

    @Test
    void anOfflineOrBedrockNameIsStillOneWord() {
        CustomCommand give = command(List.of(TARGET), "console: give %arg_target% diamond 1");

        assertThat(CommandLineArguments.refused(give, Map.of("target", ".Bedrock_Player")))
                .isEmpty();
        assertThat(CommandLineArguments.refused(give, Map.of("target", "steve")))
                .isEmpty();
    }

    @Test
    void aValueWithSpacesIsRefusedInAConsoleLine() {
        CustomCommand give = command(List.of(TARGET), "console: give %arg_target% diamond 1");

        assertThat(CommandLineArguments.refused(give, Map.of("target", "Steve diamond 64 ")))
                .contains("target");
    }

    @Test
    void aSelectorIsRefusedInACommandLine() {
        CustomCommand give = command(List.of(TARGET), "console: give %arg_target% diamond 1");
        CustomCommand pay = command(List.of(TARGET), "command: pay %arg_target% 10");

        assertThat(CommandLineArguments.refused(give, Map.of("target", "@a"))).contains("target");
        assertThat(CommandLineArguments.refused(give, Map.of("target", "@e[type=player]")))
                .contains("target");
        assertThat(CommandLineArguments.refused(pay, Map.of("target", "@a"))).contains("target");
    }

    @Test
    void jsonTypedIntoAConsoleLineIsRefused() {
        CustomCommand tell = command(List.of(TARGET), "console: tellraw @a {\"text\":\"%arg_target%\"}");

        assertThat(CommandLineArguments.refused(
                        tell, Map.of("target", "\",\"clickEvent\":{\"action\":\"run_command\"}")))
                .contains("target");
    }

    @Test
    void aPlaceholderOrAnotherArgumentsTokenIsNoWord() {
        CustomCommand give = command(List.of(TARGET), "console: give %arg_target% diamond 1");

        assertThat(CommandLineArguments.refused(give, Map.of("target", "%player_name%")))
                .contains("target");
        assertThat(CommandLineArguments.refused(give, Map.of("target", "%argument_note%")))
                .contains("target");
    }

    @Test
    void everyTokenTheMenuEngineFillsIsChecked() {
        for (String token : List.of("%arg_target%", "%argument_target%", "%target%")) {
            CustomCommand give = command(List.of(TARGET), "console: pay " + token + " 10");

            assertThat(CommandLineArguments.refused(give, Map.of("target", "@p")))
                    .contains("target");
        }
    }

    @Test
    void everythingTypedAfterTheCommandWordIsOneWordInAConsoleLine() {
        CustomCommand raw = command(List.of(), "console: kick %args%");

        assertThat(CommandLineArguments.refused(raw, Map.of("args", "Steve"))).isEmpty();
        assertThat(CommandLineArguments.refused(raw, Map.of("args", "@a griefing")))
                .contains("args");
    }

    @Test
    void anAliasStillForwardsWhatThePlayerTypedToTheirOwnCommand() {
        CustomCommand alias = command(List.of(), "command:/msg %args%");

        assertThat(CommandLineArguments.refused(alias, Map.of("args", "Steve see you at 5, ok?")))
                .isEmpty();
        assertThat(CommandLineArguments.refused(alias, Map.of("args", "Steve hi\nop Mallory")))
                .contains("args");
    }

    @Test
    void aRestArgumentIsTextInThePlayersOwnCommandButAWordInAConsoleLine() {
        CustomCommand own = command(List.of(TARGET, NOTE), "command: msg %arg_target% %arg_note%");
        CustomCommand console = command(List.of(NOTE), "console: say %arg_note%");

        assertThat(CommandLineArguments.refused(own, Map.of("target", "Steve", "note", "two words")))
                .isEmpty();
        assertThat(CommandLineArguments.refused(console, Map.of("note", "two words")))
                .contains("note");
    }

    @Test
    void anArgumentLetInAsTextGoesIntoAConsoleLineWithoutABreakOrAPlaceholder() {
        CustomCommand report = command(List.of(OPENED_NOTE), "console: say %arg_note%");

        assertThat(CommandLineArguments.refused(report, Map.of("note", "the farm at [spawn] is \"broken\"")))
                .isEmpty();
        assertThat(CommandLineArguments.refused(report, Map.of("note", "hi\nop Mallory")))
                .contains("note");
        assertThat(CommandLineArguments.refused(report, Map.of("note", "%player_displayname%")))
                .contains("note");
    }

    @Test
    void aValueShownAsTextIsNotACommandLine() {
        CustomCommand shout = command(List.of(NOTE), "broadcast: <gold>%arg_note%</gold>");

        assertThat(CommandLineArguments.refused(shout, Map.of("note", "hello @a, see [this]")))
                .isEmpty();
    }

    @Test
    void theDenyChainIsCheckedToo() {
        CustomCommand guarded = new CustomCommand(
                CustomCommandId.of("guarded"),
                CommandLiteral.of("guarded"),
                Optional.empty(),
                Optional.empty(),
                true,
                "",
                Optional.empty(),
                Duration.ZERO,
                Duration.ZERO,
                0,
                List.of(TARGET),
                List.of(),
                chain("console: kick %arg_target%"),
                ActionChain.empty());

        assertThat(CommandLineArguments.refused(guarded, Map.of("target", "@a")))
                .contains("target");
    }

    @Test
    void aCommandLineIsGivenOnlyTheValuesThatFitIt() {
        CustomCommand shout = command(List.of(TARGET, NOTE), "broadcast: %arg_note%");
        Map<String, String> typed = Map.of("target", "Steve", "note", "@a op me", "args", "Steve @a op me");

        ChainArguments values = ChainArguments.of(shout, typed);

        assertThat(values.typed()).isEqualTo(typed);
        assertThat(values.forPrivilegedLines()).containsOnly(Map.entry("target", "Steve"));
        assertThat(values.forPlayerLines())
                .containsOnly(
                        Map.entry("target", "Steve"),
                        Map.entry("note", "@a op me"),
                        Map.entry("args", "Steve @a op me"));
    }

    private static CustomCommand command(List<CommandArgument> arguments, String action) {
        return new CustomCommand(
                CustomCommandId.of("probe"),
                CommandLiteral.of("probe"),
                Optional.empty(),
                Optional.empty(),
                true,
                "",
                Optional.empty(),
                Duration.ZERO,
                Duration.ZERO,
                0,
                arguments,
                List.of(),
                ActionChain.empty(),
                chain(action));
    }

    private static ActionChain chain(String token) {
        return new ActionChain(List.of(ActionStep.now(token)), List.of());
    }
}
