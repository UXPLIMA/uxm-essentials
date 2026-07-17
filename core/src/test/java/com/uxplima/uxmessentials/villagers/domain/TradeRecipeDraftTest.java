package com.uxplima.uxmessentials.villagers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure recipe-validity rule the trade manager consults before it materialises a villager trade: a draft is
 * usable only with a result and between one and {@link TradeRecipeDraft#MAX_INGREDIENTS} ingredients, and a negative
 * ingredient count is rejected at construction.
 */
class TradeRecipeDraftTest {

    @Test
    void aResultWithOneIngredientIsValid() {
        assertThat(new TradeRecipeDraft(1, true).isValid()).isTrue();
    }

    @Test
    void aResultWithTwoIngredientsIsValid() {
        assertThat(new TradeRecipeDraft(2, true).isValid()).isTrue();
    }

    @Test
    void aResultWithNoIngredientsIsNotValid() {
        assertThat(new TradeRecipeDraft(0, true).isValid()).isFalse();
    }

    @Test
    void ingredientsWithNoResultAreNotValid() {
        assertThat(new TradeRecipeDraft(2, false).isValid()).isFalse();
    }

    @Test
    void moreIngredientsThanAMerchantRecipeHoldsIsNotValid() {
        assertThat(new TradeRecipeDraft(3, true).isValid()).isFalse();
    }

    @Test
    void aNegativeIngredientCountIsRejected() {
        assertThatThrownBy(() -> new TradeRecipeDraft(-1, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
