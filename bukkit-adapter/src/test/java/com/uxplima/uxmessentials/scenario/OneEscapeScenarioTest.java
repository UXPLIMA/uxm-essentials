package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Text that goes into a line is escaped once, by the line: a command's own syntax and what a player typed read exactly
 * as written, with no backslash left over from a second escape, and a tag in them does nothing.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OneEscapeScenarioTest {

    private static final String BOLD = "<b>loud</b>";

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start();
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void theAdminHelpShowsTheImportArgumentsAsWritten() {
        ScenarioPlayer admin = operator("Ada");

        running.run(admin, "uxmess help");

        assertShown(RunningPlugin.said(admin), "<source> [--dry-run]");
    }

    @Test
    void anImportWithoutASourceShowsItsArgumentsAsWritten() {
        ScenarioPlayer admin = operator("Abe");

        running.run(admin, "uxmess import");

        assertShown(RunningPlugin.said(admin), "<source> [--dry-run]");
    }

    @Test
    void thePermissionsHintShowsItsArgumentsAsWritten() {
        ScenarioPlayer admin = operator("Ace");

        running.run(admin, "uxmess permissions");

        assertShown(RunningPlugin.said(admin), "<area> [page]");
    }

    @Test
    void thePlaceholdersHintShowsItsArgumentsAsWritten() {
        ScenarioPlayer admin = operator("Ali");

        running.run(admin, "uxmess placeholders");

        assertShown(RunningPlugin.said(admin), "<area> [page]");
    }

    @Test
    void aCommandsUsageShowsItsArgumentsAsWritten() {
        ScenarioPlayer admin = operator("Amy");

        running.run(admin, "bank create");

        assertShown(RunningPlugin.said(admin), "<name> [currency]");
    }

    @Test
    void aGuardHelpLineShowsItsArgumentsAsWritten() {
        ScenarioPlayer admin = operator("Ann");

        running.run(admin, "guard help");

        List<String> lines = RunningPlugin.said(admin);
        assertThat(lines)
                .anySatisfy(line -> assertThat(line).contains("/guard ").contains("<"));
        assertThat(lines).noneSatisfy(line -> assertThat(line).contains("\\"));
    }

    @Test
    void aRegionsSettingReadsAsWrittenInItsInfo() {
        ScenarioPlayer admin = operator("Arn");
        running.run(admin, "guard define lobby global");
        running.run(admin, "guard set lobby message.enter " + BOLD);
        RunningPlugin.said(admin);

        running.run(admin, "guard info lobby");

        assertShown(RunningPlugin.said(admin), BOLD);
    }

    @Test
    void aBankNameWithTagsReadsAsTyped() {
        ScenarioPlayer owner = operator("Ari");

        running.run(owner, "bank create \"" + BOLD + "\"");

        assertShown(RunningPlugin.said(owner), BOLD);
    }

    @Test
    void aBankNameWithTagsReadsAsTypedInTheBankMenu() {
        ScenarioPlayer owner = operator("Bo");
        running.run(owner, "bank create \"" + BOLD + "\"");

        running.run(owner, "bank");

        assertShown(texts(owner.getOpenInventory().getTopInventory()), BOLD);
    }

    @Test
    void aBackupNamesItsFolderAsItIs() {
        ScenarioPlayer admin = operator("Ava");

        running.run(admin, "backup");
        running.settle();

        List<String> lines = RunningPlugin.said(admin);
        assertThat(lines).isNotEmpty();
        // A Windows path is all backslashes: escaped twice, each one would show doubled.
        assertThat(lines).noneSatisfy(line -> assertThat(line).contains("\\\\"));
    }

    @Test
    void whatAPlayerActsOutReadsAsTyped() {
        ScenarioPlayer actor = running.join("Mia");
        ScenarioPlayer reader = running.join("Max");

        running.run(actor, "me waves " + BOLD);

        assertShown(RunningPlugin.said(reader), "waves " + BOLD);
    }

    @Test
    void anAwayReasonReadsAsTypedInTheNoticeAndInTheReplyToAMessage() {
        ScenarioPlayer away = running.join("Ola");
        ScenarioPlayer writer = running.join("Oli");
        RunningPlugin.said(writer);

        running.run(away, "afk " + BOLD);
        List<String> notice = RunningPlugin.said(writer);
        running.run(writer, "msg Ola hello");
        List<String> reply = RunningPlugin.said(writer);

        assertShown(notice, BOLD);
        assertShown(reply, BOLD);
    }

    @Test
    void aCommandAPlayerRanReadsAsTypedToTheStaffWatching() {
        ScenarioPlayer staff = operator("Sia");
        ScenarioPlayer runner = running.join("Sam");
        running.run(staff, "commandspy");
        RunningPlugin.said(staff);

        running.run(runner, "me " + BOLD);

        assertShown(RunningPlugin.said(staff), "me " + BOLD);
    }

    @Test
    void aLineAnotherPluginAsksForShowsItsValuesAsGiven() {
        ScenarioPlayer reader = running.join("Pia");

        Component line = running.api().message(reader, "pwarp.display-name-set", Map.of("warp", BOLD));

        assertShown(List.of(PlainTextComponentSerializer.plainText().serialize(line)), BOLD);
    }

    @Test
    void aVaultNameWithTagsReadsAsTypedInTheSelector() {
        // With two vaults owned, /vault opens the picker that names each one.
        ScenarioPlayer owner = running.join("Val");
        owner.addAttachment(running.plugin, "uxmessentials.vault.amount.2", true);
        for (int vault = 1; vault <= 2; vault++) {
            running.run(owner, "vault " + vault);
            assertThat(running.place(owner, 0, new ItemStack(Material.DIAMOND))).isTrue();
            owner.closeInventory();
            running.settle();
        }
        running.run(owner, "vault rename 1 " + BOLD);

        running.run(owner, "vault");

        assertShown(texts(owner.getOpenInventory().getTopInventory()), BOLD);
    }

    /** A player the server counts as an operator, as staff are. */
    private ScenarioPlayer operator(String name) {
        ScenarioPlayer player = running.join(name);
        player.setOp(true);
        RunningPlugin.said(player);
        return player;
    }

    /** Some line holds {@code text} exactly, and no line holds a backslash nobody wrote. */
    private void assertShown(List<String> lines, String text) {
        assertThat(lines).anySatisfy(line -> assertThat(line).contains(text));
        assertThat(lines).noneSatisfy(line -> assertThat(line).contains("\\"));
        assertThat(running.problems()).isEmpty();
    }

    /** Every item name and lore line in the window, as text. */
    private static List<String> texts(Inventory window) {
        List<String> found = new ArrayList<>();
        for (@Nullable ItemStack item : window.getContents()) {
            if (item == null || !item.hasItemMeta()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta.displayName() != null) {
                found.add(plain(meta.displayName()));
            }
            List<Component> lore = meta.lore();
            if (lore != null) {
                lore.forEach(line -> found.add(plain(line)));
            }
        }
        return found;
    }

    private static String plain(@Nullable Component text) {
        return text == null ? "" : PlainTextComponentSerializer.plainText().serialize(text);
    }
}
