package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.DataComponents;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RichMeta;
import org.junit.jupiter.api.Test;

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
        assertThat(s.items().get("go").view()).extracting(Ref::id).containsExactly("warp:is-server-warp");
    }

    @Test
    void failsFastOnBadRows() {
        assertThatThrownBy(() -> new MenuSpecLoader().parse("rows = 9\nitems {}"))
                .isInstanceOf(MenuSpecException.class);
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
}
