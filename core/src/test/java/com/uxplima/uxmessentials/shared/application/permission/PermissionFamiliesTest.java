package com.uxplima.uxmessentials.shared.application.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownStartPhase;
import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupKind;
import com.uxplima.uxmessentials.teleport.application.RtpRadiusTier;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultSizeQuota;
import com.uxplima.uxmessentials.worlds.application.WorldAccessPolicy;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.Test;

/**
 * A family row is the node an operator reads in {@code /uxmess permissions} and grants; the check builds its node from
 * a factory or a head constant at runtime. The catalogue lookup matches a family by prefix only, so a row can name a
 * shape the check never builds and nothing else notices: the operator grants the listed node and the player is still
 * refused. Each row is filled in with a sample here and must equal the node its check builds from the same sample.
 */
class PermissionFamiliesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("<[^>]+>");

    /**
     * The node every family's check builds for one sample, with the sample values in the order the row's
     * placeholders appear. A family added to the catalogue needs an entry here before the build passes.
     */
    private static final List<Checked> CHECKED = List.of(
            checked(SharedPermissions.cooldown("menu.daily") + ".60", "menu.daily", "60"),
            checked(new CooldownKind("rtp", 30, CooldownStartPhase.TELEPORT).bypassNode(), "rtp"),
            checked(SharedPermissions.cooldownBypass("customcommand.daily"), "customcommand.daily"),
            checked(new WarmupKind("tp", 3).bypassNode(), "tp"),
            checked(tier(new WarmupKind("tp", 3).warmupNode(), "5"), "5"),
            checked(tier(RtpRadiusTier.FAMILY.node(), "2000"), "2000"),
            checked(tier(DestinationPermissions.HOME_LIMIT, "5"), "5"),
            checked(DestinationPermissions.warpUse("spawn"), "spawn"),
            checked(tier(PlayerWarpQuota.FAMILY.node(), "3"), "3"),
            checked(WorldAccessPolicy.enterNode(WorldName.of("lobby")), "lobby"),
            checked(tier(VaultAmountQuota.FAMILY.node(), "2"), "2"),
            checked(tier(VaultSizeQuota.FAMILY.node(), "6"), "6"),
            checked(EconomyPermissions.currency("gems"), "gems"),
            checked(tier(EconomyPermissions.ECONOMY_SALARY_AMOUNT, "100"), "100"),
            checked(tier(EnforcementPermissions.maxDuration("mute"), "3600"), "mute", "3600"),
            checked(level(EnforcementPermissions.VANISH_SEE_LEVEL, "3"), "3"),
            checked(level(EnforcementPermissions.VANISH_USE_LEVEL, "3"), "3"),
            checked(SocialPermissions.infoPage("rules"), "rules"),
            checked(ItemworldPermissions.enchantment("sharpness"), "sharpness"),
            checked(ItemworldPermissions.giveItem("diamond_sword"), "diamond_sword"),
            checked(ItemworldPermissions.spawnMob("zombie"), "zombie"),
            checked(tier(ContentPermissions.NPC_LIMIT, "4"), "4"),
            checked(ContentPermissions.kit("starter"), "starter"),
            checked(ContentPermissions.kitCooldown("vip"), "vip"),
            checked(ContentPermissions.skinName("notch"), "notch"));

    @Test
    void everyFamilyRowNamesTheNodeItsCheckBuilds() {
        List<String> unmatched = families().stream()
                .map(PermissionSpec::node)
                .filter(row -> CHECKED.stream().noneMatch(checked -> checked.isFilledFrom(row)))
                .toList();

        assertThat(unmatched)
                .as("each family row, filled with a sample, must equal the node its check builds from that sample")
                .isEmpty();
    }

    @Test
    void everyCheckedNodeIsAFamilyRowOfTheCatalogue() {
        List<String> stray = CHECKED.stream()
                .filter(checked -> families().stream().noneMatch(spec -> checked.isFilledFrom(spec.node())))
                .map(Checked::node)
                .toList();

        assertThat(stray).as("a checked node no family row lists").isEmpty();
    }

    @Test
    void theWorldEntryRowIsTheNodeTheAccessPolicyChecks() {
        String checked = WorldAccessPolicy.enterNode(WorldName.of("lobby"));

        assertThat(PermissionCatalog.find(checked))
                .map(PermissionSpec::node)
                .contains("uxmessentials.world.<world>.enter");
        assertThat(checked).isEqualTo("uxmessentials.world.lobby.enter");
    }

    private static List<PermissionSpec> families() {
        return PermissionCatalog.all().stream()
                .filter(spec -> spec.shape() != PermissionShape.FIXED)
                .toList();
    }

    /** How the quota reducer reads a held tier: the family's head, a dot, then the number. */
    private static String tier(String head, String value) {
        return head + "." + value;
    }

    /** The vanish level resolver reads the level straight after its head, with no separator. */
    private static String level(String head, String value) {
        return head + value;
    }

    private static Checked checked(String node, String... values) {
        return new Checked(node, List.of(values));
    }

    private record Checked(String node, List<String> values) {

        /** Whether {@code row}, with its placeholders replaced in order by this sample's values, is this node. */
        boolean isFilledFrom(String row) {
            Matcher placeholder = PLACEHOLDER.matcher(row);
            StringBuilder filled = new StringBuilder();
            int index = 0;
            while (placeholder.find()) {
                if (index == values.size()) {
                    return false;
                }
                placeholder.appendReplacement(filled, Matcher.quoteReplacement(values.get(index++)));
            }
            placeholder.appendTail(filled);
            return index == values.size() && filled.toString().equals(node);
        }
    }
}
