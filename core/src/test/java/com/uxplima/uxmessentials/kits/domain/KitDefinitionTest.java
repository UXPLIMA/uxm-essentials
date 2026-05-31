package com.uxplima.uxmessentials.kits.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.kits.domain.event.KitClaimed;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Value-object rules of the kits domain: id normalisation and the node it builds, the item-amount and
 * cost invariants, and the {@link KitDefinition} flags ({@code oneTime}, {@code permission}, {@code hasCost})
 * the claim gate branches on. These are the pure rules the {@code :core} layer guarantees regardless of any
 * adapter.
 */
class KitDefinitionTest {

    @Test
    void kitIdNormalisesToLowercaseAndBuildsItsNode() {
        KitId id = KitId.of("  Starter ");

        assertThat(id.value()).isEqualTo("starter");
        assertThat(id.permissionNode()).isEqualTo("uxmessentials.kit.starter");
    }

    @Test
    void kitIdRejectsBlankInput() {
        assertThatThrownBy(() -> KitId.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kitItemRejectsANonPositiveAmount() {
        assertThatThrownBy(() -> KitItem.of("payload", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kitCostFreeIsFreeAndAPriceIsNot() {
        assertThat(KitCost.free().isFree()).isTrue();
        assertThat(KitCost.of(new BigDecimal("10")).isFree()).isFalse();
    }

    @Test
    void kitCostRejectsANegativeAmount() {
        assertThatThrownBy(() -> KitCost.of(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void repeatableKitIsFreeUngatedAndNotOneTime() {
        KitDefinition kit = KitDefinition.repeatable(KitId.of("daily"), items(), Duration.ofSeconds(90));

        assertThat(kit.isOneTime()).isFalse();
        assertThat(kit.requiresPermission()).isFalse();
        assertThat(kit.hasCost()).isFalse();
        assertThat(kit.cooldownSeconds()).isEqualTo(90);
    }

    @Test
    void aOneTimePricedGatedKitReportsItsFlags() {
        KitDefinition kit = new KitDefinition(
                KitId.of("vip"), items(), Duration.ZERO, true, true, KitCost.of(new BigDecimal("500")));

        assertThat(kit.isOneTime()).isTrue();
        assertThat(kit.requiresPermission()).isTrue();
        assertThat(kit.hasCost()).isTrue();
    }

    @Test
    void aNegativeCooldownIsRejected() {
        assertThatThrownBy(() ->
                        new KitDefinition(KitId.of("x"), items(), Duration.ofSeconds(-1), false, false, KitCost.free()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kitClaimedCarriesItsRecipientAndActor() {
        PlayerRef recipient = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef actor = new PlayerRef(UUID.randomUUID(), "Operator");
        KitClaimed event = new KitClaimed(KitId.of("vip"), recipient, actor, Instant.EPOCH);

        assertThat(event.kit().value()).isEqualTo("vip");
        assertThat(event.recipient()).isEqualTo(recipient);
        assertThat(event.actor()).isEqualTo(actor);
    }

    private static List<KitItem> items() {
        return List.of(KitItem.of("payload", 1));
    }
}
