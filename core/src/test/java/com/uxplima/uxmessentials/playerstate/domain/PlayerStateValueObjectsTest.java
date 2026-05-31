package com.uxplima.uxmessentials.playerstate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * The playerstate value objects' parse/clamp rules: {@link SpeedValue} clamps to {@code 0..10} and maps onto
 * Bukkit's {@code 0..1} multiplier, {@link GameModeRef} parses names/aliases/ids, {@link PersonalTime} parses
 * presets and ticks and wraps a day, and {@link PersonalWeather} parses its tokens. These rules keep an
 * invalid argument from ever reaching a snapshot or the live player.
 */
class PlayerStateValueObjectsTest {

    @Test
    void speedClampsBelowZeroAndAboveTen() {
        assertThat(SpeedValue.of(-5.0).scale()).isZero();
        assertThat(SpeedValue.of(50.0).scale()).isEqualTo(10.0);
        assertThat(SpeedValue.of(3.0).scale()).isEqualTo(3.0);
    }

    @Test
    void speedMapsOntoTheBukkitMultiplier() {
        assertThat(SpeedValue.DEFAULT_WALK.toWalkMultiplier()).isEqualTo(0.2f, within(1.0e-6f));
        assertThat(SpeedValue.DEFAULT_FLY.toFlyMultiplier()).isEqualTo(0.1f, within(1.0e-6f));
        // The top of the scale caps just below Bukkit's 1.0 bound so /speed 10 never produces a rejected value.
        assertThat(SpeedValue.of(10.0).toWalkMultiplier()).isLessThan(1.0f).isGreaterThan(0.98f);
    }

    @Test
    void gameModeParsesNamesAliasesAndIds() {
        assertThat(GameModeRef.parse("creative")).contains(GameModeRef.CREATIVE);
        assertThat(GameModeRef.parse("C")).contains(GameModeRef.CREATIVE);
        assertThat(GameModeRef.parse("1")).contains(GameModeRef.CREATIVE);
        assertThat(GameModeRef.parse("SURVIVAL")).contains(GameModeRef.SURVIVAL);
        assertThat(GameModeRef.parse("sp")).contains(GameModeRef.SPECTATOR);
        assertThat(GameModeRef.parse("nonsense")).isEmpty();
    }

    @Test
    void personalTimeParsesResetPresetsAndTicks() {
        assertThat(PersonalTime.parse("reset"))
                .get()
                .extracting(PersonalTime::reset)
                .isEqualTo(true);
        assertThat(PersonalTime.parse("night"))
                .get()
                .extracting(PersonalTime::ticks)
                .isEqualTo(14_000L);
        assertThat(PersonalTime.parse("1500"))
                .get()
                .extracting(PersonalTime::ticks)
                .isEqualTo(1_500L);
        assertThat(PersonalTime.parse("words")).isEmpty();
    }

    @Test
    void personalTimeWrapsADay() {
        assertThat(PersonalTime.parse("25000"))
                .get()
                .extracting(PersonalTime::ticks)
                .isEqualTo(1_000L);
    }

    @Test
    void personalWeatherParsesItsTokens() {
        assertThat(PersonalWeather.parse("clear")).contains(PersonalWeather.CLEAR);
        assertThat(PersonalWeather.parse("RAIN")).contains(PersonalWeather.RAIN);
        assertThat(PersonalWeather.parse("storm")).contains(PersonalWeather.RAIN);
        assertThat(PersonalWeather.parse("reset")).contains(PersonalWeather.RESET);
        assertThat(PersonalWeather.parse("snow")).isEmpty();
    }
}
