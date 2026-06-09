package com.uxplima.uxmessentials.kits.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.domain.KitCost;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.kits.domain.KitVariant;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Per-rank variant resolution in {@link KitAccess}: the best variant whose permission the viewer holds wins,
 * the base kit applies when they hold none, and a variant's optional cooldown and cost overrides replace the
 * base values while every other setting is inherited. This is the pure rule that lets one {@code /kit daily}
 * hand richer loot to higher ranks.
 */
class KitVariantResolutionTest {

    private static final String VIP = "uxmessentials.kit.tier.vip";
    private static final String MVP = "uxmessentials.kit.tier.mvp";

    private final PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");

    @Test
    void resolvesTheFirstVariantWhosePermissionTheViewerHolds() {
        KitDefinition kit = tiered();
        StubPermissions perms = new StubPermissions();
        perms.grant(alice, MVP); // holds only mvp, the second (lower) tier

        KitDefinition resolved = access(perms).resolveVariant(alice, kit);

        assertThat(resolved.items()).containsExactly(KitItem.of("mvp-loot", 1));
        assertThat(resolved.cooldownSeconds()).isEqualTo(900);
    }

    @Test
    void prefersTheBestVariantWhenTheViewerHoldsSeveral() {
        KitDefinition kit = tiered();
        StubPermissions perms = new StubPermissions();
        perms.grant(alice, VIP);
        perms.grant(alice, MVP);

        KitDefinition resolved = access(perms).resolveVariant(alice, kit);

        // vip is listed first (best-first order), so it wins over the also-held mvp tier
        assertThat(resolved.items()).containsExactly(KitItem.of("vip-loot", 1));
        assertThat(resolved.cooldownSeconds()).isEqualTo(1800);
    }

    @Test
    void fallsBackToTheBaseKitWhenTheViewerHoldsNoVariantPermission() {
        KitDefinition kit = tiered();

        KitDefinition resolved = access(new StubPermissions()).resolveVariant(alice, kit);

        assertThat(resolved.items()).containsExactly(KitItem.of("base-loot", 1));
        assertThat(resolved.cooldownSeconds()).isEqualTo(3600);
    }

    @Test
    void aVariantWithoutAnOverrideInheritsTheBaseCooldownAndCost() {
        KitVariant inheriting = KitVariant.of(VIP, List.of(KitItem.of("vip-loot", 1)));
        KitDefinition kit = KitDefinition.repeatable(
                        KitId.of("daily"), List.of(KitItem.of("base-loot", 1)), Duration.ofMinutes(10))
                .withCost(KitCost.of(new BigDecimal("50")))
                .withVariants(List.of(inheriting));
        StubPermissions perms = new StubPermissions();
        perms.grant(alice, VIP);

        KitDefinition resolved = access(perms).resolveVariant(alice, kit);

        assertThat(resolved.items()).containsExactly(KitItem.of("vip-loot", 1));
        assertThat(resolved.cooldownSeconds()).isEqualTo(600);
        assertThat(resolved.cost().amount()).isEqualByComparingTo("50");
    }

    @Test
    void aVariantCostOverrideIsChargedInsteadOfTheBaseCost() {
        KitVariant cheaper = new KitVariant(
                VIP,
                List.of(KitItem.of("vip-loot", 1)),
                Optional.empty(),
                Optional.of(KitCost.of(new BigDecimal("10"))));
        KitDefinition kit = KitDefinition.repeatable(
                        KitId.of("daily"), List.of(KitItem.of("base-loot", 1)), Duration.ZERO)
                .withCost(KitCost.of(new BigDecimal("999")))
                .withVariants(List.of(cheaper));
        StubPermissions perms = new StubPermissions();
        perms.grant(alice, VIP);
        RecordingEconomy economy = new RecordingEconomy();

        Result<Unit, ?> admitted = access(perms, Optional.of(economy)).admit(alice, kit);

        assertThat(admitted.isOk()).isTrue();
        assertThat(economy.charged).isEqualByComparingTo("10"); // the variant's price, not the base 999
    }

    private static KitDefinition tiered() {
        return KitDefinition.repeatable(KitId.of("daily"), List.of(KitItem.of("base-loot", 1)), Duration.ofHours(1))
                .withVariants(List.of(
                        new KitVariant(
                                VIP,
                                List.of(KitItem.of("vip-loot", 1)),
                                Optional.of(Duration.ofMinutes(30)),
                                Optional.empty()),
                        new KitVariant(
                                MVP,
                                List.of(KitItem.of("mvp-loot", 1)),
                                Optional.of(Duration.ofMinutes(15)),
                                Optional.empty())));
    }

    private KitAccess access(Permissions perms) {
        return access(perms, Optional.empty());
    }

    private KitAccess access(Permissions perms, Optional<KitEconomy> economy) {
        return new KitAccess(perms, new AlwaysReady(), new NoClaims(), economy);
    }

    private static final class StubPermissions implements Permissions {
        private final Map<UUID, Set<String>> granted = new HashMap<>();

        void grant(PlayerRef who, String node) {
            granted.computeIfAbsent(who.uuid(), u -> new HashSet<>()).add(node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.getOrDefault(who.uuid(), Set.of()).contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @org.jspecify.annotations.Nullable WorldRef world, long fallback) {
            return QuotaResult.limited(fallback);
        }
    }

    private static final class AlwaysReady implements Cooldowns {
        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class NoClaims implements KitClaimStore {
        @Override
        public boolean hasClaimed(PlayerRef who, KitId kit) {
            return false;
        }

        @Override
        public void markClaimed(PlayerRef who, KitId kit) {}

        @Override
        public void reset(PlayerRef who, KitId kit) {}

        @Override
        public void resetAll(PlayerRef who) {}
    }

    private static final class RecordingEconomy implements KitEconomy {
        private BigDecimal charged = BigDecimal.ZERO;

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            charged = charged.add(amount);
            return true;
        }

        @Override
        public boolean deposit(PlayerRef who, BigDecimal amount, String currencyId) {
            return true;
        }
    }
}
