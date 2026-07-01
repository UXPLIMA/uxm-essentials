package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The material-spec → Bedrock button image heuristic in isolation, no Bukkit or Cumulus needed. A skull spec becomes
 * an mc-heads avatar URL (or {@code null} for an unresolvable base64/URL value); any other spec becomes a best-effort
 * {@code textures/<category>/<name>} path. The asserted strings are the exact output of the ported heuristic.
 */
class BedrockIconsTest {

    private static final UUID VIEWER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void aNamedSkullBecomesAnMcHeadsAvatarUrl() {
        BedrockImage image = BedrockIcons.forMaterialSpec("skull:Notch", VIEWER);
        assertThat(image).isEqualTo(new BedrockImage(BedrockImage.Kind.URL, "https://mc-heads.net/avatar/Notch"));
    }

    @Test
    void aHeadPrefixWithAUuidResolvesThatUuid() {
        String uuid = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
        BedrockImage image = BedrockIcons.forMaterialSpec("head:" + uuid, VIEWER);
        assertThat(image).isEqualTo(new BedrockImage(BedrockImage.Kind.URL, "https://mc-heads.net/avatar/" + uuid));
    }

    @Test
    void selfResolvesToTheViewerUuid() {
        BedrockImage image = BedrockIcons.forMaterialSpec("skull:self", VIEWER);
        assertThat(image).isEqualTo(new BedrockImage(BedrockImage.Kind.URL, "https://mc-heads.net/avatar/" + VIEWER));
    }

    @Test
    void anItemMaterialBecomesAnItemsTexturePath() {
        BedrockImage image = BedrockIcons.forMaterialSpec("DIAMOND_SWORD", VIEWER);
        assertThat(image).isEqualTo(new BedrockImage(BedrockImage.Kind.PATH, "textures/items/diamond_sword"));
    }

    @Test
    void aCuratedBlockBecomesABlocksTexturePath() {
        BedrockImage image = BedrockIcons.forMaterialSpec("STONE", VIEWER);
        assertThat(image).isEqualTo(new BedrockImage(BedrockImage.Kind.PATH, "textures/blocks/stone"));
    }

    @Test
    void aSuffixedBlockBecomesABlocksTexturePath() {
        BedrockImage image = BedrockIcons.forMaterialSpec("OAK_LOG", VIEWER);
        assertThat(image).isEqualTo(new BedrockImage(BedrockImage.Kind.PATH, "textures/blocks/oak_log"));
    }

    @Test
    void aSpawnEggTakesTheBedrockSpawnStemRemap() {
        BedrockImage image = BedrockIcons.forMaterialSpec("ZOMBIE_SPAWN_EGG", VIEWER);
        assertThat(image).isEqualTo(new BedrockImage(BedrockImage.Kind.PATH, "textures/items/spawn_zombie"));
    }

    @Test
    void aMusicDiscTakesTheBedrockRecordStemRemap() {
        BedrockImage image = BedrockIcons.forMaterialSpec("MUSIC_DISC_CAT", VIEWER);
        assertThat(image).isEqualTo(new BedrockImage(BedrockImage.Kind.PATH, "textures/items/record_cat"));
    }

    @Test
    void airHasNoIcon() {
        assertThat(BedrockIcons.forMaterialSpec("AIR", VIEWER)).isNull();
    }

    @Test
    void aBlankSpecHasNoIcon() {
        assertThat(BedrockIcons.forMaterialSpec("   ", VIEWER)).isNull();
    }

    @Test
    void aBase64SkullValueHasNoIconBecauseMcHeadsCannotRenderIt() {
        // A raw base64 textures payload is far longer than a dashed UUID and carries no name mc-heads can look up.
        String base64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcyJ9fX0=";
        assertThat(BedrockIcons.forMaterialSpec("skull:" + base64, VIEWER)).isNull();
    }

    @Test
    void aBaseheadSpecHasNoIcon() {
        assertThat(BedrockIcons.forMaterialSpec("basehead:eyJ0ZXh0dXJlcyI6e319", VIEWER))
                .isNull();
    }

    @Test
    void aSkinUrlSkullValueHasNoIcon() {
        assertThat(BedrockIcons.forMaterialSpec("skull:https://textures.minecraft.net/texture/abc", VIEWER))
                .isNull();
    }
}
