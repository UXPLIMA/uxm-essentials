package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import org.junit.jupiter.api.Test;

class BankInterestPolicyTest {

    private static BankInterestPolicy tiered(boolean enabled) {
        return new BankInterestPolicy(
                enabled,
                Duration.ofHours(24),
                List.of(
                        new BankInterestPolicy.Tier(BigDecimal.ZERO, new BigDecimal("0.5")),
                        new BankInterestPolicy.Tier(new BigDecimal("100000"), new BigDecimal("1.0"))));
    }

    @Test
    void appliesTheHighestQualifyingTierRate() {
        BankInterestPolicy policy = tiered(true);

        assertThat(policy.interestOn(Money.of(Currencies.COINS, 50_000)))
                .isEqualTo(Money.of(Currencies.COINS, 250)); // 0.5% of 50k
        assertThat(policy.interestOn(Money.of(Currencies.COINS, 100_000)))
                .isEqualTo(Money.of(Currencies.COINS, 1000)); // 1% of 100k (the higher tier)
    }

    @Test
    void belowEveryTierMinimumEarnsNothing() {
        BankInterestPolicy policy = new BankInterestPolicy(
                true,
                Duration.ofHours(24),
                List.of(new BankInterestPolicy.Tier(new BigDecimal("1000"), new BigDecimal("1.0"))));

        assertThat(policy.interestOn(Money.of(Currencies.COINS, 500)).isZero()).isTrue();
    }

    @Test
    void disabledPolicyAccruesNothing() {
        assertThat(tiered(false).interestOn(Money.of(Currencies.COINS, 100_000)).isZero())
                .isTrue();
    }
}
