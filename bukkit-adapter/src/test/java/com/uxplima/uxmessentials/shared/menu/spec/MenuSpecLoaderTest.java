package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.DataComponents;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Requirement;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RequirementSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RichMeta;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

class MenuSpecLoaderTest {

    private static final String HOCON = """
            title = "@menu.test.title"
            rows = 3
            refresh { enabled = true, interval-ticks = 20 }
            open-requirement = [ "perm:x" ]
            items {
              border { slots = ["0-2"], material = GRAY_STAINED_GLASS_PANE, name = "" }
              go { slot = 4, material = "%icon%", name = "@n", view = ["warp:is-server-warp"], priority = 5,
                   click { left = ["warp:set-icon"], right = ["close"] }, update = true }
            }
            """;

    @Test
    void parsesMenu() {
        MenuSpec s = new MenuSpecLoader().parse(HOCON);
        assertThat(s.rows()).isEqualTo(3);
        assertThat(s.refresh().enabled()).isTrue();
        assertThat(s.items().get("border").slots().slots()).containsExactly(0, 1, 2);
        assertThat(s.items().get("go").click().actionsFor(ClickKind.LEFT))
                .extracting(Ref::id)
                .containsExactly("warp:set-icon");
        assertThat(s.items().get("go").view().requirements())
                .extracting(r -> r.condition().id())
                .containsExactly("warp:is-server-warp");
    }

    @Test
    void failsFastOnBadRows() {
        assertThatThrownBy(() -> new MenuSpecLoader().parse("rows = 9\nitems {}"))
                .isInstanceOf(MenuSpecException.class);
    }

    @Test
    void parsesAFlatViewListWithInversionAsAnAndBlock() {
        String hocon = "rows=1\nitems{ x{ slot=0, material=STONE, view=[\"has-money:100\", \"!has-empty-slots:1\"] } }";
        RequirementSpec view =
                new MenuSpecLoader().parse(hocon).items().get("x").view();

        assertThat(view.minimum())
                .as("a flat view list is an all-mandatory AND block")
                .isZero();
        assertThat(view.requirements())
                .extracting(r -> r.condition().id(), Requirement::inverted)
                .containsExactly(tuple("has-money:100", false), tuple("has-empty-slots:1", true));
    }

    @Test
    void parsesAMapViewBlockWithAMinimum() {
        String hocon = "rows=1\nitems{ x{ slot=0, material=STONE,"
                + " view={ requirements=[\"has-empty-slots:1\", \"has-empty-slots:9\"], minimum=1 } } }";
        RequirementSpec view =
                new MenuSpecLoader().parse(hocon).items().get("x").view();

        assertThat(view.minimum()).isEqualTo(1);
        assertThat(view.effectiveMinimum())
                .as("minimum 1 over two conditions is an OR")
                .isEqualTo(1);
        assertThat(view.requirements())
                .extracting(r -> r.condition().id())
                .containsExactly("has-empty-slots:1", "has-empty-slots:9");
    }

    @Test
    void anAbsentViewIsTheEmptyBlock() {
        RequirementSpec view = new MenuSpecLoader()
                .parse("rows=1\nitems{ x{ slot=0, material=STONE } }")
                .items()
                .get("x")
                .view();
        assertThat(view).isEqualTo(RequirementSpec.NONE);
    }

    private static final String RICH = """
            rows = 1
            items {
              thing {
                slot = 0
                material = DIAMOND_SWORD
                decor {
                  amount = "%count%"
                  model-data = 7
                  glow = true
                  flags = ["HIDE_ATTRIBUTES"]
                  unbreakable = true
                  enchantments = ["sharpness:5", "unbreaking:3"]
                  stored-enchantments = ["mending:1"]
                  leather-color = "#A1FF33"
                  potion { type = STRENGTH, color = "#00AAFF", effects = ["speed:1:600"] }
                  banner { patterns = ["stripe_top:red", "circle:white"] }
                  trim { material = diamond, pattern = sentry }
                  damage = 100
                  item-model = "minecraft:diamond_sword"
                }
              }
            }
            """;

    @Test
    void parsesRichDecorIntoStringTokens() {
        ItemDecor decor = new MenuSpecLoader().parse(RICH).items().get("thing").decor();
        RichMeta meta = decor.meta();

        // A %placeholder% amount stays dynamic with the static amount kept at its default; model-data parses as an int.
        assertThat(decor.amount()).isEqualTo(1);
        assertThat(meta.dynamicAmount()).contains("%count%");
        assertThat(decor.modelData()).contains(7);
        assertThat(meta.dynamicModelData()).isEmpty();

        assertThat(meta.unbreakable()).isTrue();
        assertThat(meta.enchantments()).containsExactly("sharpness:5", "unbreaking:3");
        assertThat(meta.storedEnchantments()).containsExactly("mending:1");
        assertThat(meta.leatherColor()).contains("#A1FF33");
        assertThat(meta.potion().type()).contains("STRENGTH");
        assertThat(meta.potion().color()).contains("#00AAFF");
        assertThat(meta.potion().effects()).containsExactly("speed:1:600");
        assertThat(meta.bannerPatterns()).containsExactly("stripe_top:red", "circle:white");
        assertThat(meta.trim()).contains(new RichMeta.TrimSpec("diamond", "sentry"));
        assertThat(meta.damage()).contains(100);
        assertThat(meta.itemModel()).contains("minecraft:diamond_sword");
    }

    @Test
    void dynamicModelDataTokenIsCarriedAndStaticIsLeftEmpty() {
        ItemDecor decor = new MenuSpecLoader()
                .parse("rows=1\nitems{ x{ slot=0, decor{ model-data = \"%md%\" } } }")
                .items()
                .get("x")
                .decor();

        assertThat(decor.modelData()).isEmpty();
        assertThat(decor.meta().dynamicModelData()).contains("%md%");
    }

    @Test
    void absentDecorIsRichMetaNone() {
        ItemDecor decor = new MenuSpecLoader()
                .parse("rows=1\nitems{ x{ slot=0, material=STONE } }")
                .items()
                .get("x")
                .decor();

        assertThat(decor.meta()).isEqualTo(RichMeta.NONE);
        assertThat(decor.meta().components()).isEqualTo(DataComponents.NONE);
        assertThat(decor.amount()).isEqualTo(1);
    }

    private static final String COMPONENTS = """
            rows = 1
            items {
              thing {
                slot = 0
                material = DIAMOND_SWORD
                decor {
                  rarity = EPIC
                  tooltip-style = "minecraft:fancy"
                  hide-tooltip = true
                  enchant-glint = true
                  enchantable = 10
                  attribute-modifiers = ["generic.attack_damage:5:add_number:hand", "generic.max_health:2:add_number:any"]
                  food { nutrition = 4, saturation = 2.4, can-always-eat = true }
                  tool { default-mining-speed = 1.0, damage-per-block = 2 }
                }
              }
            }
            """;

    @Test
    void parsesDataComponentsIntoTokens() {
        DataComponents components = new MenuSpecLoader()
                .parse(COMPONENTS)
                .items()
                .get("thing")
                .decor()
                .meta()
                .components();

        assertThat(components.rarity()).contains("EPIC");
        assertThat(components.tooltipStyle()).contains("minecraft:fancy");
        assertThat(components.hideTooltip()).contains(true);
        assertThat(components.enchantGlint()).contains(true);
        assertThat(components.enchantable()).contains(10);
        assertThat(components.attributeModifiers())
                .containsExactly("generic.attack_damage:5:add_number:hand", "generic.max_health:2:add_number:any");
        assertThat(components.food())
                .contains(new DataComponents.FoodSpec(
                        java.util.Optional.of(4), java.util.Optional.of(2.4), java.util.Optional.of(true)));
        assertThat(components.tool())
                .contains(new DataComponents.ToolSpec(java.util.Optional.of(1.0), java.util.Optional.of(2)));
    }

    private static final String MODIFIERS = """
            rows = 1
            items {
              b {
                slot = 0
                material = DIAMOND
                click {
                  left = [
                    { do = "command:eco give 100", delay = 20, chance = 25, deny = "message:none" }
                    "sound:UI_BUTTON_CLICK"
                  ]
                }
              }
            }
            """;

    @Test
    void parsesTheMapFormWithDelayChanceAndDenyAlongsidePlainScalars() {
        List<Ref> actions =
                new MenuSpecLoader().parse(MODIFIERS).items().get("b").click().actionsFor(ClickKind.LEFT);

        assertThat(actions).hasSize(2);

        Ref first = actions.get(0);
        assertThat(first.id()).isEqualTo("command");
        assertThat(first.value()).isEqualTo("eco give 100");
        assertThat(first.delayTicks()).isEqualTo(20);
        assertThat(first.chance()).isEqualTo(25.0);
        assertThat(first.deny()).map(Ref::id).contains("message");

        // The scalar entry parses exactly as before — no modifiers.
        Ref second = actions.get(1);
        assertThat(second.id()).isEqualTo("sound");
        assertThat(second.delayTicks()).isZero();
        assertThat(second.chance()).isEqualTo(100.0);
        assertThat(second.deny()).isEmpty();
    }

    @Test
    void mapEntryWithoutAnActionTokenIsSkipped() {
        List<Ref> actions = new MenuSpecLoader()
                .parse("rows=1\nitems{ b{ slot=0, click{ left = [ { chance = 50 }, \"close\" ] } } }")
                .items()
                .get("b")
                .click()
                .actionsFor(ClickKind.LEFT);

        assertThat(actions).extracting(Ref::id).containsExactly("close");
    }

    @Test
    void aPlainStringListStillParsesUnchanged() {
        List<Ref> actions = new MenuSpecLoader()
                .parse("rows=1\nitems{ b{ slot=0, click{ left = [\"give\", \"close\"] } } }")
                .items()
                .get("b")
                .click()
                .actionsFor(ClickKind.LEFT);

        assertThat(actions).extracting(Ref::id).containsExactly("give", "close");
        assertThat(actions).allSatisfy(ref -> {
            assertThat(ref.delayTicks()).isZero();
            assertThat(ref.chance()).isEqualTo(100.0);
            assertThat(ref.deny()).isEmpty();
        });
    }

    @Test
    void unsetToggleStaysEmptySoItNeverOverridesTheItem() {
        DataComponents components = new MenuSpecLoader()
                .parse("rows=1\nitems{ x{ slot=0, decor{ rarity = RARE } } }")
                .items()
                .get("x")
                .decor()
                .meta()
                .components();

        assertThat(components.rarity()).contains("RARE");
        assertThat(components.hideTooltip()).isEmpty();
        assertThat(components.enchantGlint()).isEmpty();
        assertThat(components.food()).isEmpty();
        assertThat(components.tool()).isEmpty();
        assertThat(components.attributeModifiers()).isEmpty();
    }

    @Test
    void parsesTheInventoryTypeTokenFromTheMenuRoot() {
        MenuSpec spec = new MenuSpecLoader().parse("inventory-type = \"hopper\"\nitems{ x{ slot=0, material=STONE } }");

        assertThat(spec.inventoryType()).contains("hopper");
    }

    @Test
    void anAbsentInventoryTypeIsAnEmptyOptionalAndTheMenuIsAChest() {
        MenuSpec spec = new MenuSpecLoader().parse("rows=1\nitems{ x{ slot=0, material=STONE } }");

        assertThat(spec.inventoryType())
                .as("no inventory-type node means the default rows-based chest")
                .isEmpty();
    }

    @Test
    void aBlankInventoryTypeIsAnEmptyOptional() {
        MenuSpec spec = new MenuSpecLoader().parse("inventory-type = \"\"\nrows=1\nitems{ x{ slot=0 } }");

        assertThat(spec.inventoryType()).isEmpty();
    }

    @Test
    void aNonChestMenuNeedNotDeclareRowsAndKeepsOversizeSlotsForRenderToSkip() {
        // A hopper spec omits rows: the loader defaults rows to the largest chest so slot 8 loads (it exceeds the
        // hopper's five slots but not the chest fallback), to be skipped later at render rather than rejected here.
        MenuSpec spec = new MenuSpecLoader()
                .parse("inventory-type = \"hopper\"\nitems{ a{ slots=[\"0-4\"], material=STONE }, b{ slot=8 } }");

        assertThat(spec.rows()).isEqualTo(6);
        assertThat(spec.items().get("b").slots().slots()).containsExactly(8);
    }

    @Test
    void theDelegatingConstructorDefaultsTheInventoryTypeToEmpty() {
        MenuSpec spec =
                new MenuSpec("t", 1, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), java.util.Map.of());

        assertThat(spec.inventoryType())
                .as("the seven-argument constructor keeps every existing call-site on the default chest")
                .isEmpty();
    }

    @Test
    void aPositiveUpdateIntervalKeyEnablesTheRefreshAtThatCadence() {
        RefreshSpec refresh = new MenuSpecLoader()
                .parse("update-interval = 40\nrows = 1\nitems {}")
                .refresh();

        assertThat(refresh).isEqualTo(new RefreshSpec(true, 40));
    }

    @Test
    void aRefreshBlockAloneIsHonouredWhenNoUpdateIntervalIsSet() {
        RefreshSpec refresh = new MenuSpecLoader()
                .parse("rows = 1\nrefresh { enabled = true, interval-ticks = 20 }\nitems {}")
                .refresh();

        assertThat(refresh).isEqualTo(new RefreshSpec(true, 20));
    }

    @Test
    void neitherUpdateIntervalNorARefreshBlockLeavesRefreshDisabled() {
        RefreshSpec refresh = new MenuSpecLoader().parse("rows = 1\nitems {}").refresh();

        assertThat(refresh).isEqualTo(new RefreshSpec(false, 0));
    }

    @Test
    void updateIntervalWinsOverARefreshBlockWhenBothAreSet() {
        RefreshSpec refresh = new MenuSpecLoader()
                .parse("update-interval = 40\nrefresh { enabled = true, interval-ticks = 20 }\nrows = 1\nitems {}")
                .refresh();

        assertThat(refresh)
                .as("the convenience update-interval key takes precedence over an explicit refresh block")
                .isEqualTo(new RefreshSpec(true, 40));
    }

    @Test
    void aNonPositiveUpdateIntervalFallsBackToTheRefreshBlock() {
        RefreshSpec refresh = new MenuSpecLoader()
                .parse("update-interval = 0\nrefresh { enabled = true, interval-ticks = 15 }\nrows = 1\nitems {}")
                .refresh();

        assertThat(refresh)
                .as("update-interval only takes over when it is a positive tick count")
                .isEqualTo(new RefreshSpec(true, 15));
    }

    @Test
    void aChestWithoutRowsAutoSizesToFitItsHighestSlot() {
        MenuSpec spec = new MenuSpecLoader()
                .parse("items { a { slot = 0, material = STONE }, b { slot = 20, material = STONE } }");

        assertThat(spec.rows()).as("a slot in the third row needs three rows").isEqualTo(3);
    }

    @Test
    void aChestAutoSizesToFiveRowsForASlotInTheFifthRow() {
        MenuSpec spec = new MenuSpecLoader().parse("items { a { slot = 40, material = STONE } }");

        assertThat(spec.rows()).isEqualTo(5);
    }

    @Test
    void anExplicitButTooSmallRowsGrowsToFitAHigherSlot() {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 2\nitems { a { slot = 30, material = STONE } }");

        assertThat(spec.rows())
                .as("a declared two rows grows to four to hold a slot in the fourth row")
                .isEqualTo(4);
    }

    @Test
    void aBareChestWithNoItemsIsOneRowNotZero() {
        MenuSpec spec = new MenuSpecLoader().parse("items {}");

        assertThat(spec.rows()).isEqualTo(1);
    }

    @Test
    void anExplicitRowsThatAlreadyFitsEverySlotIsUnchanged() {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 3\nitems { a { slot = 26, material = STONE } }");

        assertThat(spec.rows())
                .as("three declared rows already hold every slot below 27, so the count is untouched")
                .isEqualTo(3);
    }

    @Test
    void aPackedChestCapsAtSixRows() {
        MenuSpec spec = new MenuSpecLoader().parse("items { a { slot = 53, material = STONE } }");

        assertThat(spec.rows())
                .as("a slot in the last of six rows sizes the chest to the six-row ceiling")
                .isEqualTo(6);
    }

    @Test
    void aListTemplateSlotAlsoSizesTheChest() {
        MenuSpec spec = new MenuSpecLoader()
                .parse("items { grid { list { source = \"warps:all\", template { slot = 30, material = STONE } } } }");

        assertThat(spec.rows())
                .as("a paginated list's content slots count toward the auto-sized rows")
                .isEqualTo(4);
    }

    @Test
    void aChestSlotBeyondTheSixRowMaximumIsAFailFastConfigError() {
        // A chest renders at most six rows (54 slots); a slot past that can never be shown, so — consistent with the
        // loader's fail-fast slot check and the six-row ceiling the auto-sizer parses against — it is a loud error.
        assertThatThrownBy(() -> new MenuSpecLoader().parse("items { a { slot = 60, material = STONE } }"))
                .isInstanceOf(MenuSpecException.class);
    }

    private static final String PATTERNS = """
            rows = 1
            patterns {
              shop-button {
                material = "%mat%"
                name = "<gold>%label%"
                lore = ["<gray>Click to buy %label%", "<gray>Price: %price%"]
                click { left = ["open:%target%"] }
                defaults { mat = "STONE", price = "0" }
              }
            }
            items {
              diamonds {
                pattern = "shop-button"
                slots = [0]
                vars { mat = "DIAMOND", label = "Diamonds", target = "diamond-shop", price = "5" }
                name = "<aqua>%label% (deal)"
              }
            }
            """;

    @Test
    void aPatternResolvesItsVarsIntoTheItemAndTheItemFieldWins() {
        MenuItemSpec item = new MenuSpecLoader().parse(PATTERNS).items().get("diamonds");

        assertThat(item.material()).isEqualTo("DIAMOND");
        assertThat(item.name())
                .as("the item's own name overrides the pattern's, and its own %label% is filled too")
                .isEqualTo("<aqua>Diamonds (deal)");
        assertThat(item.lore()).containsExactly("<gray>Click to buy Diamonds", "<gray>Price: 5");
        assertThat(item.click().actionsFor(ClickKind.LEFT))
                .extracting(Ref::id, Ref::value)
                .containsExactly(tuple("open", "diamond-shop"));
        assertThat(item.slots().slots()).containsExactly(0);
    }

    @Test
    void anOmittedVarFallsBackToThePatternDefault() {
        String hocon = """
                rows = 1
                patterns { p { name = "Price: %price%", defaults { price = "0" } } }
                items { x { slot = 0, pattern = "p", vars { } } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon).items().get("x");

        assertThat(item.name())
                .as("price omitted from vars falls back to the pattern default")
                .isEqualTo("Price: 0");
    }

    @Test
    void anUnknownTokenIsLeftVerbatimForRenderTime() {
        String hocon = """
                rows = 1
                patterns { p { material = STONE, lore = ["<gray>%player_name%", "<gray>%label%"] } }
                items { x { slot = 0, pattern = "p", vars { label = "Hi" } } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon).items().get("x");

        assertThat(item.lore())
                .as("a declared var is filled now; an undeclared %placeholder% stays for the renderer")
                .containsExactly("<gray>%player_name%", "<gray>Hi");
    }

    @Test
    void substitutionRecursesIntoNestedMapsAndLists() {
        String hocon = """
                rows = 1
                patterns {
                  p {
                    material = "%mat%"
                    decor { potion { type = "%ptype%", effects = ["%effect%"] } }
                  }
                }
                items { x { slot = 0, pattern = "p",
                            vars { mat = "POTION", ptype = "STRENGTH", effect = "speed:1:600" } } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon).items().get("x");

        assertThat(item.material()).isEqualTo("POTION");
        assertThat(item.decor().meta().potion().type()).contains("STRENGTH");
        assertThat(item.decor().meta().potion().effects()).containsExactly("speed:1:600");
    }

    @Test
    void anItemClickOverrideReplacesTheTemplateClickWholesale() {
        String hocon = """
                rows = 1
                patterns { p { material = STONE, click { left = ["close"], right = ["open:a"] } } }
                items { x { slot = 0, pattern = "p", click { right = ["open:b"] } } }
                """;
        ClickSpec click = new MenuSpecLoader().parse(hocon).items().get("x").click();

        assertThat(click.actionsFor(ClickKind.RIGHT))
                .extracting(Ref::id, Ref::value)
                .containsExactly(tuple("open", "b"));
        assertThat(click.actionsFor(ClickKind.LEFT))
                .as("an item click block replaces the whole template click, so the template's left gesture is gone")
                .isEmpty();
    }

    @Test
    void anItemNamingAnUnknownPatternParsesFromItsOwnFields() {
        String hocon = "rows=1\nitems { x { slot = 0, pattern = \"nope\", material = EMERALD, name = \"Own\" } }";
        MenuItemSpec item = new MenuSpecLoader().parse(hocon).items().get("x");

        assertThat(item.material())
                .as("an unknown pattern name is warned about, not fatal; the item parses its own fields")
                .isEqualTo("EMERALD");
        assertThat(item.name()).isEqualTo("Own");
    }

    @Test
    void aPatternBlockDoesNotAffectAnItemThatDoesNotReferenceIt() {
        String hocon = """
                rows = 1
                patterns { p { material = DIAMOND } }
                items { plain { slot = 0, material = STONE, name = "Plain" } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon).items().get("plain");

        assertThat(item.material()).isEqualTo("STONE");
        assertThat(item.name()).isEqualTo("Plain");
        assertThat(item.slots().slots()).containsExactly(0);
    }

    @Test
    void aNestedPatternKeyOnATemplateIsIgnoredResolvingOnlyOneLevel() {
        String hocon = """
                rows = 1
                patterns {
                  base { material = STONE }
                  derived { pattern = "base", material = DIAMOND, name = "%who%" }
                }
                items { x { slot = 0, pattern = "derived", vars { who = "Steve" } } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon).items().get("x");

        // Resolution runs one level only: 'derived' is used as written — its own pattern="base" key is ignored — so
        // the material stays DIAMOND rather than being pulled down to STONE from the base template.
        assertThat(item.material()).isEqualTo("DIAMOND");
        assertThat(item.name()).isEqualTo("Steve");
    }

    @Test
    void aGlobalPatternResolvesForAMenuWithNoLocalPatterns() throws Exception {
        ConfigurationNode global = patternsNode("""
                patterns { hub-button { material = "%mat%", name = "<gold>%label%" } }
                """);
        String hocon = """
                rows = 1
                items { hub { slots = [0], pattern = "hub-button", vars { mat = "DIAMOND", label = "Hub" } } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon, global).items().get("hub");

        assertThat(item.material())
                .as("a pattern from the shared file resolves for a menu that declares none of its own")
                .isEqualTo("DIAMOND");
        assertThat(item.name()).isEqualTo("<gold>Hub");
    }

    @Test
    void aMenuLocalPatternOverridesAGlobalOfTheSameName() throws Exception {
        ConfigurationNode global = patternsNode("""
                patterns { button { material = STONE, name = "Global" } }
                """);
        String hocon = """
                rows = 1
                patterns { button { material = DIAMOND, name = "Local" } }
                items { x { slot = 0, pattern = "button" } }
                """;
        MenuItemSpec item = new MenuSpecLoader().parse(hocon, global).items().get("x");

        assertThat(item.material())
                .as("the menu's own template wins the name clash with the shared one")
                .isEqualTo("DIAMOND");
        assertThat(item.name()).isEqualTo("Local");
    }

    @Test
    void anUndefinedPatternWithEmptyGlobalsParsesItsOwnFieldsWithoutThrowing() throws Exception {
        String hocon = "rows=1\nitems { x { slot = 0, pattern = \"missing\", material = EMERALD } }";
        MenuItemSpec item =
                new MenuSpecLoader().parse(hocon, patternsNode("")).items().get("x");

        assertThat(item.material())
                .as("no shared patterns and an undefined name is the slice-A warn path, not a throw")
                .isEqualTo("EMERALD");
    }

    @Test
    void theNoGlobalsOverloadEqualsTheGlobalsOverloadWithAnEmptyNode() throws Exception {
        MenuSpec withoutGlobals = new MenuSpecLoader().parse(PATTERNS);
        MenuSpec withEmptyGlobals = new MenuSpecLoader().parse(PATTERNS, patternsNode(""));

        assertThat(withEmptyGlobals)
                .as("passing an empty node delegates byte-identically, so the pattern-free overload is unchanged")
                .isEqualTo(withoutGlobals);
    }

    @Test
    void aListTemplateResolvesAGlobalPattern() throws Exception {
        ConfigurationNode global = patternsNode("""
                patterns { row { material = "%mat%", name = "<gray>%label%" } }
                """);
        String hocon = """
                rows = 1
                items {
                  grid {
                    list {
                      source = "warps:all"
                      template { slots = [0], pattern = "row", vars { mat = "PAPER", label = "Warp" } }
                    }
                  }
                }
                """;
        MenuItemSpec template = new MenuSpecLoader()
                .parse(hocon, global)
                .items()
                .get("grid")
                .list()
                .orElseThrow()
                .template();

        assertThat(template.material())
                .as("a list template naming a shared pattern is expanded per entry from that shared pattern")
                .isEqualTo("PAPER");
        assertThat(template.name()).isEqualTo("<gray>Warp");
    }

    @Test
    void aListTemplateResolvesAMenuLocalPattern() {
        // Confirms slice-A already covers list-expansion: parseList feeds its template through parseItem with the
        // pattern map, so a list whose template names a (here menu-local) pattern is stamped from it per entry.
        String hocon = """
                rows = 1
                patterns { entry { material = "%mat%", name = "%label%" } }
                items {
                  grid {
                    list {
                      source = "warps:all"
                      template { slots = [0], pattern = "entry", vars { mat = "MAP", label = "Home" } }
                    }
                  }
                }
                """;
        MenuItemSpec template = new MenuSpecLoader()
                .parse(hocon)
                .items()
                .get("grid")
                .list()
                .orElseThrow()
                .template();

        assertThat(template.material()).isEqualTo("MAP");
        assertThat(template.name()).isEqualTo("Home");
    }

    /** Parse a HOCON document and hand back its {@code patterns} node, the shape a shared {@code patterns.conf} holds. */
    private static ConfigurationNode patternsNode(String hocon) throws Exception {
        return HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(hocon)))
                .build()
                .load()
                .node("patterns");
    }
}
