package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
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
        assertThat(decor.amount()).isEqualTo(1);
    }
}
