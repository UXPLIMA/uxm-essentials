package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorldPropertiesTest {

    @Test
    void voidRescueDecodesAChainAndRefusesATypo() {
        assertThat(WorldProperties.VOID_RESCUE.decode("warp:hub;spawn"))
                .contains(VoidRescueChain.parse("warp:hub;spawn").orElseThrow());
        assertThat(WorldProperties.VOID_RESCUE.decode("")).contains(VoidRescueChain.none());
        assertThat(WorldProperties.VOID_RESCUE.decode("bed")).isEmpty();
        assertThat(WorldProperties.VOID_RESCUE.encode(
                        VoidRescueChain.parse("at:lobby,0,80,0").orElseThrow()))
                .isEqualTo("at:lobby,0,80,0");
    }

    @Test
    void voidRescueTriggerHeightIsSignedAndClearable() {
        assertThat(WorldProperties.VOID_RESCUE_Y.decode("-24")).contains(java.util.Optional.of(-24));
        assertThat(WorldProperties.VOID_RESCUE_Y.decode("")).contains(java.util.Optional.empty());
        assertThat(WorldProperties.VOID_RESCUE_Y.decode("deep")).isEmpty();
        assertThat(WorldProperties.VOID_RESCUE_Y.encode(java.util.Optional.empty()))
                .isEmpty();
        assertThat(WorldProperties.VOID_RESCUE_Y.defaultValue()).isEmpty();
    }

    @Test
    void byKeyResolvesEveryRegisteredProperty() {
        for (WorldProperty<?> property : WorldProperties.ALL) {
            assertThat(WorldProperties.byKey(property.key())).containsSame(property);
        }
        assertThat(WorldProperties.byKey("nope")).isEmpty();
    }

    @Test
    void boolPropertyDecodesAndRejects() {
        assertThat(WorldProperties.PVP.decode("true")).contains(true);
        assertThat(WorldProperties.PVP.decode("FALSE")).contains(false);
        assertThat(WorldProperties.PVP.decode("yes")).isEmpty();
        assertThat(WorldProperties.PVP.encode(true)).isEqualTo("true");
    }

    @Test
    void enumPropertiesDecodeCaseInsensitivelyAndReject() {
        assertThat(WorldProperties.DIFFICULTY.decode("hard")).contains(WorldDifficulty.HARD);
        assertThat(WorldProperties.DIFFICULTY.decode("nope")).isEmpty();
        assertThat(WorldProperties.FORCE_GAMEMODE.decode("creative")).contains(ForcedGameMode.CREATIVE);
        assertThat(WorldProperties.WEATHER.decode("thunder")).contains(WeatherLock.THUNDER);
    }

    @Test
    void timeDecodesNonNegativeTicks() {
        assertThat(WorldProperties.TIME.decode("6000")).contains(6000L);
        assertThat(WorldProperties.TIME.decode("-1")).isEmpty();
        assertThat(WorldProperties.TIME.decode("noon")).isEmpty();
    }

    @Test
    void suggestionsAreNonEmptyForEnumAndBool() {
        assertThat(WorldProperties.DIFFICULTY.suggestions()).contains("HARD");
        assertThat(WorldProperties.PVP.suggestions()).contains("true", "false");
    }
}
