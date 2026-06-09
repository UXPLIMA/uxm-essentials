package com.uxplima.uxmessentials.kits.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.RequirementEvaluator;
import com.uxplima.uxmessentials.kits.domain.KitCost;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.kits.domain.KitRequirement;
import com.uxplima.uxmessentials.kits.domain.RequirementOperator;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The placeholder-requirement gate added to {@link KitAccess#admit}. Requirements are evaluated after the
 * permission, one-time, and cooldown gates and before the charge, so a player who fails them sees
 * {@link KitError#REQUIREMENTS_NOT_MET} rather than being charged. The evaluator is soft-coupled: present, it
 * decides each condition; absent, a kit that declares requirements <em>fails closed</em>. This exercises the
 * gate against a fake evaluator (no PlaceholderAPI) so the logic is proven in {@code :core}, off any adapter.
 */
class KitRequirementGateTest {

    private StubPermissions permissions;
    private ReadyCooldowns cooldowns;
    private NoClaims claims;
    private PlayerRef alice;

    @BeforeEach
    void setUp() {
        permissions = new StubPermissions();
        cooldowns = new ReadyCooldowns();
        claims = new NoClaims();
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    @Test
    void aKitWithNoRequirementsIsAdmittedEvenWithNoEvaluator() {
        KitAccess access = access(Optional.empty());

        Result<Unit, KitError> result = access.admit(alice, kit(List.of()));

        assertThat(result.isOk()).isTrue();
    }

    @Test
    void aKitWithRequirementsFailsClosedWhenNoEvaluatorIsPresent() {
        KitAccess access = access(Optional.empty());

        Result<Unit, KitError> result =
                access.admit(alice, kit(List.of(req("%player_level%", RequirementOperator.GTE, "10"))));

        assertThat(result.errorOrThrow()).isEqualTo(KitError.REQUIREMENTS_NOT_MET);
        assertThat(access.meetsRequirements(alice, kit(List.of(req("%a%", RequirementOperator.GTE, "1")))))
                .isFalse();
    }

    @Test
    void aKitWhoseRequirementsAllPassIsAdmitted() {
        KitAccess access = access(Optional.of(new FakeEvaluator(true)));

        Result<Unit, KitError> result =
                access.admit(alice, kit(List.of(req("%player_level%", RequirementOperator.GTE, "10"))));

        assertThat(result.isOk()).isTrue();
        assertThat(access.meetsRequirements(alice, kit(List.of(req("%a%", RequirementOperator.GTE, "1")))))
                .isTrue();
    }

    @Test
    void aKitWithAFailingRequirementIsRefusedWithRequirementsNotMet() {
        KitAccess access = access(Optional.of(new FakeEvaluator(false)));

        Result<Unit, KitError> result =
                access.admit(alice, kit(List.of(req("%player_level%", RequirementOperator.GTE, "10"))));

        assertThat(result.errorOrThrow()).isEqualTo(KitError.REQUIREMENTS_NOT_MET);
    }

    @Test
    void thePermissionGateStillRunsBeforeTheRequirementGate() {
        KitAccess access = access(Optional.of(new FakeEvaluator(false)));
        KitDefinition gated = new KitDefinition(KitId.of("vip"), items(), Duration.ZERO, false, true, KitCost.free())
                .withRequirements(List.of(req("%a%", RequirementOperator.EQ, "b")));

        // Without the permission, the more informative permission refusal wins over the requirement refusal.
        Result<Unit, KitError> result = access.admit(alice, gated);

        assertThat(result.errorOrThrow()).isEqualTo(KitError.NO_PERMISSION);
    }

    private KitAccess access(Optional<RequirementEvaluator> evaluator) {
        return new KitAccess(permissions, cooldowns, claims, Optional.empty(), evaluator);
    }

    private static KitDefinition kit(List<KitRequirement> reqs) {
        return KitDefinition.repeatable(KitId.of("daily"), items(), Duration.ZERO)
                .withRequirements(reqs);
    }

    private static KitRequirement req(String left, RequirementOperator op, String right) {
        return new KitRequirement(left, op, right);
    }

    private static List<KitItem> items() {
        return List.of(KitItem.of("payload", 1));
    }

    /** An evaluator whose every condition passes or fails per the constructor flag. */
    private record FakeEvaluator(boolean verdict) implements RequirementEvaluator {
        @Override
        public boolean passes(PlayerRef who, KitRequirement requirement) {
            return verdict;
        }
    }

    private static final class ReadyCooldowns implements Cooldowns {
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

    private static final class StubPermissions implements Permissions {
        private final Map<UUID, Set<String>> granted = new HashMap<>();

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
}
