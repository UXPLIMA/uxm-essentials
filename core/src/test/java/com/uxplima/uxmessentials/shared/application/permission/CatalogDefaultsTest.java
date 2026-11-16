package com.uxplima.uxmessentials.shared.application.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * A node nobody set is answered with the catalogue's default when it belongs to a name family, and with the server's
 * own answer otherwise. The fake holder answers an unset node the way the server answers a node it was never told
 * about: operators only.
 */
class CatalogDefaultsTest {

    private static final String WARP_SPAWN = DestinationPermissions.warpUse("spawn");
    private static final String NETHER_ENTRY = DestinationPermissions.worldEnter("nether");

    @Test
    void anUnsetWarpNodeIsOpenToAPlayerWhoIsNotAnOperator() {
        assertThat(CatalogDefaults.holds(FakeHolder.player(), WARP_SPAWN)).isTrue();
    }

    @Test
    void anInfoPageAnOperatorAddedStaysClosedToAPlayerUntilGranted() {
        String staffPage = SocialPermissions.infoPage("staff");

        assertThat(CatalogDefaults.holds(FakeHolder.player(), staffPage)).isFalse();
        assertThat(CatalogDefaults.holds(FakeHolder.operator(), staffPage)).isTrue();
        assertThat(CatalogDefaults.holds(FakeHolder.player().set(staffPage, true), staffPage))
                .isTrue();
    }

    @Test
    void aShippedInfoPageIsLeftToItsRegisteredDefaultOfEveryone() {
        String rules = SocialPermissions.COMMUNICATION_INFO_RULES;

        assertThat(CatalogDefaults.familyDefault(rules)).isEmpty();
        assertThat(PermissionCatalog.find(rules)).map(PermissionSpec::fallback).contains(PermissionDefault.TRUE);
    }

    @Test
    void anExplicitNegationClosesAnOpenFamilyNode() {
        FakeHolder holder = FakeHolder.player().set(WARP_SPAWN, false);

        assertThat(CatalogDefaults.holds(holder, WARP_SPAWN)).isFalse();
    }

    @Test
    void anOperatorFamilyNodeIsClosedToAPlayerAndOpenToAnOperator() {
        assertThat(CatalogDefaults.holds(FakeHolder.player(), NETHER_ENTRY)).isFalse();
        assertThat(CatalogDefaults.holds(FakeHolder.operator(), NETHER_ENTRY)).isTrue();
    }

    @Test
    void anExplicitGrantOpensAnOperatorFamilyNodeToAPlayer() {
        FakeHolder holder = FakeHolder.player().set(NETHER_ENTRY, true);

        assertThat(CatalogDefaults.holds(holder, NETHER_ENTRY)).isTrue();
    }

    @Test
    void aFalseFamilyNodeIsClosedEvenToAnOperator() {
        assertThat(CatalogDefaults.holds(FakeHolder.operator(), ContentPermissions.kitCooldown("vip")))
                .isFalse();
    }

    @Test
    void aReservedKitOrSkinStaysClosedToAPlayerUntilGranted() {
        assertThat(CatalogDefaults.holds(FakeHolder.player(), ContentPermissions.kit("vip")))
                .isFalse();
        assertThat(CatalogDefaults.holds(FakeHolder.player(), ContentPermissions.skinName("notch")))
                .isFalse();
    }

    @Test
    void aNegatedWildcardClosesTheFamilyOnAServerThatDoesNotResolveWildcards() {
        FakeHolder holder = FakeHolder.player().set(DestinationPermissions.warpUse("*"), false);

        assertThat(CatalogDefaults.holds(holder, WARP_SPAWN)).isFalse();
    }

    @Test
    void aGrantedWildcardOpensAnOperatorFamilyToAPlayer() {
        FakeHolder holder = FakeHolder.player().set("uxmessentials.world.*", true);

        assertThat(CatalogDefaults.holds(holder, NETHER_ENTRY)).isTrue();
    }

    @Test
    void theNearestWildcardDecides() {
        FakeHolder holder =
                FakeHolder.player().set("uxmessentials.*", true).set(DestinationPermissions.warpUse("*"), false);

        assertThat(CatalogDefaults.holds(holder, WARP_SPAWN)).isFalse();
    }

    @Test
    void aNodeTheCatalogueDoesNotKnowKeepsTheServersAnswer() {
        assertThat(CatalogDefaults.holds(FakeHolder.player(), "otherplugin.fly"))
                .isFalse();
        assertThat(CatalogDefaults.holds(FakeHolder.operator(), "otherplugin.fly"))
                .isTrue();
    }

    @Test
    void aFixedNodeKeepsTheServersAnswer() {
        // A fixed node is registered with its default, so the server's answer already is the catalogue's.
        assertThat(CatalogDefaults.holds(FakeHolder.player(), DestinationPermissions.WARP_USE))
                .isFalse();
    }

    @Test
    void aNumberedTierIsNotHandedToEveryoneByItsFamilysDefault() {
        String homeTier = DestinationPermissions.HOME_LIMIT + ".500";
        String noTeleportWait = SharedPermissions.featureCooldown("tp") + ".0";

        assertThat(CatalogDefaults.holds(FakeHolder.player(), homeTier)).isFalse();
        assertThat(CatalogDefaults.holds(FakeHolder.player(), noTeleportWait)).isFalse();
    }

    @Test
    void aFamilyWhosePlaceholderSitsInTheMiddleCoversOnlyItsOwnShape() {
        assertThat(CatalogDefaults.familyDefault(NETHER_ENTRY)).contains(PermissionDefault.OP);
        assertThat(CatalogDefaults.familyDefault("uxmessentials.world.nether")).isEmpty();
    }

    @Test
    void aNodeThatRunsPastAFamilysShapeMatchesNothing() {
        assertThat(CatalogDefaults.familyDefault("uxmessentials.world.foo.enter.bar"))
                .isEmpty();
        assertThat(CatalogDefaults.holds(FakeHolder.player(), "uxmessentials.world.foo.enter.bar"))
                .isFalse();
    }

    @Test
    void aKitCooldownTierProbedByNameReadsAsAGroupNobodyWasGiven() {
        // The tier family and the group family share the shape uxmessentials.kit.cooldown.<word>; the group's
        // default answers a probe, while the tier itself is only ever read from the nodes a player holds.
        String noKitWait = SharedPermissions.featureCooldown("kit") + ".0";

        assertThat(CatalogDefaults.familyDefault(noKitWait)).contains(PermissionDefault.FALSE);
        assertThat(CatalogDefaults.holds(FakeHolder.operator(), noKitWait)).isFalse();
        assertThat(CatalogDefaults.holds(FakeHolder.player().set(noKitWait, true), noKitWait))
                .isTrue();
    }

    @Test
    void everyNameFamilyAnswersWithItsOwnDefault() {
        for (PermissionSpec spec : PermissionCatalog.all()) {
            if (spec.shape() != PermissionShape.LABEL) {
                continue;
            }
            String member = spec.node().replaceFirst("<[^>]+>", "zz-sample");

            assertThat(CatalogDefaults.familyDefault(member)).as(spec.node()).contains(spec.fallback());
        }
    }

    /** A holder with explicit settings, answering an unset node as the server answers one it does not know. */
    private static final class FakeHolder implements PermissionHolder {

        private final boolean op;
        private final Map<String, Boolean> settings = new HashMap<>();

        private FakeHolder(boolean op) {
            this.op = op;
        }

        static FakeHolder player() {
            return new FakeHolder(false);
        }

        static FakeHolder operator() {
            return new FakeHolder(true);
        }

        FakeHolder set(String node, boolean value) {
            settings.put(node, value);
            return this;
        }

        @Override
        public boolean isPermissionSet(String node) {
            return settings.containsKey(node);
        }

        @Override
        public boolean hasPermission(String node) {
            return settings.getOrDefault(node, op);
        }

        @Override
        public boolean isOp() {
            return op;
        }
    }
}
