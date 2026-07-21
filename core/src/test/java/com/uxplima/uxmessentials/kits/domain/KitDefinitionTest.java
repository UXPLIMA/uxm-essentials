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
        KitDefinition kit = KitDefinition.builder()
                .id(KitId.of("vip"))
                .items(items())
                .oneTime(true)
                .permission(true)
                .cost(KitCost.of(new BigDecimal("500")))
                .build();

        assertThat(kit.isOneTime()).isTrue();
        assertThat(kit.requiresPermission()).isTrue();
        assertThat(kit.hasCost()).isTrue();
    }

    @Test
    void aNegativeCooldownIsRejected() {
        assertThatThrownBy(() -> KitDefinition.builder()
                        .id(KitId.of("x"))
                        .items(items())
                        .cooldown(Duration.ofSeconds(-1))
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withItemsSwapsTheStacksAndPreservesEveryOtherSetting() {
        KitDefinition original = KitDefinition.builder()
                .id(KitId.of("vip"))
                .items(List.of(KitItem.of("old", 1)))
                .cooldown(Duration.ofSeconds(120))
                .oneTime(true)
                .permission(true)
                .cost(KitCost.of(new BigDecimal("250")))
                .display(new ItemDisplay(
                        java.util.Optional.of("DIAMOND"),
                        java.util.Optional.of("<gold>VIP Kit"),
                        List.of("line-one", "line-two")))
                .commands(List.of("say claimed", "give @s bread"))
                .sound(java.util.Optional.of("entity.player.levelup"))
                .particles(java.util.Optional.of("FLAME"))
                .firstJoin(true)
                .autoEquip(true)
                .categoryId(java.util.Optional.of("premium"))
                .claimMoney(new BigDecimal("99"))
                .claimMoneyCurrency("coins")
                .permissionCooldowns(java.util.Map.of("uxmessentials.kit.cooldown.30", Duration.ofSeconds(30)))
                .priority(7)
                .noPermission(new ItemDisplay(
                        java.util.Optional.of("BARRIER"),
                        java.util.Optional.of("<red>No permission"),
                        List.of("locked")))
                .cooldownDisplay(new ItemDisplay(
                        java.util.Optional.of("CLOCK"), java.util.Optional.of("<yellow>On cooldown"), List.of("wait")))
                .claimed(new ItemDisplay(
                        java.util.Optional.of("STRUCTURE_VOID"),
                        java.util.Optional.of("<gray>Claimed"),
                        List.of("done")))
                .unaffordable(new ItemDisplay(
                        java.util.Optional.of("RED_WOOL"),
                        java.util.Optional.of("<red>Too poor"),
                        List.of("need money")))
                .build();

        List<KitItem> newItems = List.of(KitItem.of("new", 5), KitItem.of("second", 2));
        KitDefinition edited = original.withItems(newItems);

        assertThat(edited.items()).isEqualTo(newItems);
        assertThat(edited.id()).isEqualTo(original.id());
        assertThat(edited.cooldown()).isEqualTo(original.cooldown());
        assertThat(edited.oneTime()).isEqualTo(original.oneTime());
        assertThat(edited.permission()).isEqualTo(original.permission());
        assertThat(edited.cost()).isEqualTo(original.cost());
        assertThat(edited.display().name()).isEqualTo(original.display().name());
        assertThat(edited.display().material()).isEqualTo(original.display().material());
        assertThat(edited.display().lore()).isEqualTo(original.display().lore());
        assertThat(edited.commands()).isEqualTo(original.commands());
        assertThat(edited.sound()).isEqualTo(original.sound());
        assertThat(edited.particles()).isEqualTo(original.particles());
        assertThat(edited.firstJoin()).isEqualTo(original.firstJoin());
        assertThat(edited.autoEquip()).isEqualTo(original.autoEquip());
        assertThat(edited.categoryId()).isEqualTo(original.categoryId());
        assertThat(edited.claimMoney()).isEqualTo(original.claimMoney());
        assertThat(edited.claimMoneyCurrency()).isEqualTo(original.claimMoneyCurrency());
        assertThat(edited.permissionCooldowns()).isEqualTo(original.permissionCooldowns());
        assertThat(edited.priority()).isEqualTo(original.priority());
        assertThat(edited.noPermission().material())
                .isEqualTo(original.noPermission().material());
        assertThat(edited.noPermission().name())
                .isEqualTo(original.noPermission().name());
        assertThat(edited.noPermission().lore())
                .isEqualTo(original.noPermission().lore());
        assertThat(edited.cooldownDisplay().material())
                .isEqualTo(original.cooldownDisplay().material());
        assertThat(edited.cooldownDisplay().name())
                .isEqualTo(original.cooldownDisplay().name());
        assertThat(edited.cooldownDisplay().lore())
                .isEqualTo(original.cooldownDisplay().lore());
        assertThat(edited.claimed().material()).isEqualTo(original.claimed().material());
        assertThat(edited.claimed().name()).isEqualTo(original.claimed().name());
        assertThat(edited.claimed().lore()).isEqualTo(original.claimed().lore());
        assertThat(edited.unaffordable().material())
                .isEqualTo(original.unaffordable().material());
        assertThat(edited.unaffordable().name())
                .isEqualTo(original.unaffordable().name());
        assertThat(edited.unaffordable().lore())
                .isEqualTo(original.unaffordable().lore());
    }

    @Test
    void customPermissionOverridesTheDefaultPerKitNode() {
        KitDefinition base = KitDefinition.repeatable(KitId.of("vip"), items(), Duration.ZERO);
        assertThat(base.permissionNode()).isEqualTo("uxmessentials.kit.vip");

        KitDefinition shared = base.withCustomPermission(java.util.Optional.of("myserver.vip.kit"));
        assertThat(shared.permissionNode()).isEqualTo("myserver.vip.kit");
    }

    @Test
    void previewDefaultsOnAndCloseOnClaimDefaultsOff() {
        KitDefinition kit = KitDefinition.repeatable(KitId.of("starter"), items(), Duration.ZERO);

        assertThat(kit.preview()).isTrue();
        assertThat(kit.closeOnClaim()).isFalse();
        assertThat(kit.withPreview(false).preview()).isFalse();
        assertThat(kit.withCloseOnClaim(true).closeOnClaim()).isTrue();
    }

    @Test
    void aVariantRejectsBlankPermissionAndEmptyItems() {
        assertThatThrownBy(() -> KitVariant.of("  ", items())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KitVariant.of("node", List.of())).isInstanceOf(IllegalArgumentException.class);
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
