package com.uxplima.uxmessentials.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import org.junit.jupiter.api.Test;

/**
 * Freezes the two boot-time invariants {@code PluginModule.applyCatalog} relies on as it folds the full
 * default command surface into the catalog. The chokepoint wraps every registered literal in {@code new
 * CommandId(...)} (which rejects anything outside {@code [a-z][a-z0-9]*}) and keys the effective surface
 * with {@code Collectors.toMap} (which throws on a duplicate id). Either failure crashes plugin enable
 * with nothing to catch it; this guard makes a new module's colliding or non-conforming literal fail CI
 * here instead of at runtime.
 */
class CommandCatalogDriftTest {

    // The literals PluginModule.wire registers directly before applyCatalog runs, none of which is
    // sourced from a feature module: /uxmess (UxmessCommand.ROOT_LITERAL) is the admin root, /lang
    // (LangCommand.LITERAL) is the cross-cutting locale switch, /backup (BackupCommand.LITERAL) is the
    // operator data snapshot, and /help (HelpCommand.LITERAL) is the cross-cutting command listing. They
    // share the same catalog namespace as the module commands, so they belong in the checks below.
    private static final List<String> BOOTSTRAP_LITERALS = List.of("uxmess", "lang", "backup", "help");

    private static List<String> allCommandLiterals() {
        List<String> moduleLiterals = new DefaultModuleRegistry()
                .all().stream()
                        .flatMap(module -> module.commands().stream())
                        .map(CommandSpec::literal)
                        .toList();
        return Stream.concat(moduleLiterals.stream(), BOOTSTRAP_LITERALS.stream())
                .toList();
    }

    @Test
    void everyCommandLiteralIsAValidCommandId() {
        // Mirrors the new CommandId(r.commandId()) the chokepoint runs, so this fails the same way enable
        // would when a literal breaks the lowercase-letters/digits shape CommandId enforces.
        for (String literal : allCommandLiterals()) {
            assertThatCode(() -> new CommandId(literal))
                    .as("command literal '%s' must be a valid CommandId", literal)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void commandIdsAreGloballyUniqueSoTheCatalogMapNeverClobbers() {
        List<String> all = allCommandLiterals();

        assertThat(all)
                .as("a duplicate commandId would make PluginModule.applyCatalog's Collectors.toMap throw at enable")
                .doesNotHaveDuplicates();

        // Reproduce the exact toMap the chokepoint builds to prove the collector itself never throws an
        // IllegalStateException for the current surface.
        assertThatCode(() -> all.stream().collect(Collectors.toMap(literal -> literal, literal -> literal)))
                .doesNotThrowAnyException();
    }
}
