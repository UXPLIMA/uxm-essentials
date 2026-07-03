package com.uxplima.uxmessentials.custommenus.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.command.OpenCommandSpec;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecWriter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import org.junit.jupiter.api.Test;

/**
 * Plain-JUnit coverage of {@link MenuEditSession}, the mutable edit model the menu editor mutates before it writes.
 * The linchpin proof is a round-trip: clone a parsed spec, mutate it through the model's operations, {@code toSpec()}
 * it, serialize with {@link MenuSpecWriter}, re-load through {@link MenuSpecLoader}, and assert it equals the spec an
 * operator would have written by hand — so the edit model and the writer compose without loss.
 */
class MenuEditSessionTest {

    private final MenuSpecLoader loader = new MenuSpecLoader();
    private final MenuSpecWriter writer = new MenuSpecWriter();

    @Test
    void cloneMutateAndWriteRoundTripsToTheExpectedSpec() {
        MenuSpec initial = loader.parse("""
                title = "<gold>Old"
                rows = 1
                items { a { slot = 0, material = STONE, name = "", click { left = ["close"] } } }
                """);
        MenuSpec expected = loader.parse("""
                title = "<red>New"
                rows = 3
                inventory-type = "hopper"
                items {
                  a { slots = [2], material = STONE, name = "", click { left = ["close"] } }
                  b { slots = [4], material = DIRT, name = "", click { left = ["close"] } }
                }
                """);

        MenuEditSession session = MenuEditSession.from(initial);
        session.setTitle("<red>New");
        session.setRows(3);
        session.setInventoryType("hopper");
        session.moveItem("a", new SlotSet(List.of(2)));
        session.addItem("b", expected.items().get("b"));

        MenuSpec reloaded = loader.parse(writer.write(session.toSpec()));
        assertThat(reloaded).isEqualTo(expected);
    }

    @Test
    void addItemAndRemoveItemChangeTheItemMap() {
        MenuSpec initial = loader.parse("""
                rows = 1
                items {
                  a { slot = 0, material = STONE, click { left = ["close"] } }
                  b { slot = 1, material = DIRT, click { left = ["close"] } }
                }
                """);
        MenuEditSession session = MenuEditSession.from(initial);

        session.removeItem("a");

        assertThat(session.toSpec().items()).containsOnlyKeys("b");
        assertThat(session.item("a")).isEmpty();
        assertThat(session.item("b")).isPresent();
    }

    @Test
    void moveItemPreservesEveryOtherFieldOfTheItem() {
        MenuSpec initial = loader.parse("""
                rows = 2
                items { a { slot = 0, priority = 3, material = STONE, name = "<green>Buy", click { left = ["close"] } } }
                """);
        MenuEditSession session = MenuEditSession.from(initial);

        session.moveItem("a", new SlotSet(List.of(9)));

        var moved = session.item("a").orElseThrow();
        assertThat(moved.slots().slots()).containsExactly(9);
        assertThat(moved.priority()).isEqualTo(3);
        assertThat(moved.material()).isEqualTo("STONE");
        assertThat(moved.name()).isEqualTo("<green>Buy");
    }

    @Test
    void menuLevelSettersReadBackThroughToSpec() {
        MenuEditSession session = MenuEditSession.from(loader.parse("rows = 1"));

        session.setTitle("<aqua>Panel");
        session.setRows(6);
        session.setClickCooldownMs(250L);
        session.setChestOnly(true);
        session.setBottomInventory(false);

        MenuSpec spec = session.toSpec();
        assertThat(spec.title()).isEqualTo("<aqua>Panel");
        assertThat(spec.rows()).isEqualTo(6);
        assertThat(spec.clickCooldownMs()).isEqualTo(250L);
        assertThat(spec.chestOnly()).isTrue();
    }

    @Test
    void clearingTheInventoryTypeReturnsToTheChestDefault() {
        MenuEditSession session = MenuEditSession.from(loader.parse("rows = 3\ninventory-type = \"hopper\"\n"));
        assertThat(session.toSpec().inventoryType()).contains("hopper");

        session.setInventoryType(null);

        assertThat(session.toSpec().inventoryType()).isEmpty();
    }

    @Test
    void theCommandBlockRidesTheSessionAndWritesBackWithTheSpec() {
        MenuSpec spec =
                loader.parse("rows = 1\nitems { x { slot = 0, material = STONE, click { left = [\"close\"] } } }");
        OpenCommandSpec command = new OpenCommandSpec(
                "shop",
                List.of("store"),
                Optional.of("menu.shop"),
                Optional.empty(),
                false,
                List.of(new ArgumentSpec("target", ArgumentSpec.ArgType.ONLINE_PLAYER)),
                Optional.of("/shop"));

        MenuEditSession session = MenuEditSession.from(spec, command);

        assertThat(session.command()).contains(command);
        // The written file carries the command {} block, so re-reading it reproduces the same open command.
        String hocon = writer.write(session.toSpec(), session.command().orElse(null));
        assertThat(hocon).contains("command");
        assertThat(hocon).contains("store");
    }
}
