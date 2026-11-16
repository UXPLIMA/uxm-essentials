package com.uxplima.uxmessentials.shared.application.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
 * The other way round, every node the code builds from a catalogue factory, a cooldown or warmup kind or a quota
 * family must be listed by a row, so nothing is checked that an operator cannot find.
 */
class PermissionFamiliesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("<[^>]+>");

    private static final List<Class<?>> CATALOGS = List.of(
            SharedPermissions.class,
            TeleportPermissions.class,
            DestinationPermissions.class,
            EconomyPermissions.class,
            EnforcementPermissions.class,
            SocialPermissions.class,
            PlayerstatePermissions.class,
            ItemworldPermissions.class,
            ContentPermissions.class);

    /**
     * The factories that need no family row of their own: every module's node is a fixed row, the custom command
     * node is only a suggestion the wizard writes into the operator's file, and the feature cooldown and warmup heads
     * have one row per feature, which {@link #everyCooldownAndWarmupTheCodeBuildsHasATierRow} checks against the
     * kinds the code builds.
     */
    private static final Set<String> ROWLESS_FACTORIES =
            Set.of("module", "customCommand", "featureCooldown", "featureWarmup");

    /** A quota family made in the code, with the head it is given as written. */
    private static final Pattern QUOTA_FAMILY =
            Pattern.compile("QuotaFamily\\.(?:quota|threshold|stack)\\(\\s*([^;]*?)\\)\\s*[;:]");

    /** A head named through a catalogue: a constant, or a factory called with one argument. */
    private static final Pattern CATALOG_HEAD = Pattern.compile("(\\w+Permissions)\\.(\\w+)(\\()?.*");

    /**
     * The files that build a quota family from a head handed to them, a cooldown or warmup kind's or a label's.
     * Those heads come from the kinds and the label factory, which the other tests here cover.
     */
    private static final Set<String> PASS_THROUGH =
            Set.of("CooldownTiers.java", "SchedulerWarmups.java", "TrackingWarmups.java");

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
            checked(tier(new WarmupKind("customcommand", 0).warmupNode(), "2"), "2"),
            checked(tier(cooldownNode("tp"), "10"), "10"),
            checked(tier(cooldownNode("rtp"), "60"), "60"),
            checked(tier(cooldownNode("kit"), "3600"), "3600"),
            checked(tier(cooldownNode("poses"), "5"), "5"),
            checked(tier(cooldownNode("skin"), "30"), "30"),
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
    void everyCatalogueFactoryBuildsANodeAFamilyRowLists() throws IllegalAccessException, InvocationTargetException {
        List<String> rowless = new ArrayList<>();
        for (Class<?> catalog : CATALOGS) {
            for (Method factory : catalog.getDeclaredMethods()) {
                if (!isFactory(factory) || ROWLESS_FACTORIES.contains(factory.getName())) {
                    continue;
                }
                String node = (String) factory.invoke(null, "sample");
                if (!isListed(new Checked(node, List.of("sample")))
                        && !isListed(new Checked(tier(node, "1"), List.of("sample", "1")))) {
                    rowless.add(catalog.getSimpleName() + "." + factory.getName() + " builds " + node);
                }
            }
        }

        assertThat(rowless).as("a factory whose node no family row lists").isEmpty();
    }

    @Test
    void everyCooldownAndWarmupTheCodeBuildsHasATierRow() {
        List<String> problems = new ArrayList<>();
        Set<String> heads = new TreeSet<>();
        for (MainSources.Kind kind : MainSources.kinds()) {
            if (kind.feature().isEmpty()) {
                problems.add(kind.file() + ": cannot tell which feature " + kind.argument() + " names");
                continue;
            }
            String feature = kind.feature().get();
            String head = kind.warmup() ? new WarmupKind(feature, 0).warmupNode() : cooldownNode(feature);
            heads.add(head);
            if (!isListed(new Checked(tier(head, "1"), List.of("1")))) {
                problems.add(kind.file() + " checks " + head + ".<seconds>, which no family row lists");
            }
        }

        assertThat(heads)
                .contains(
                        cooldownNode("tp"),
                        cooldownNode("rtp"),
                        cooldownNode("kit"),
                        cooldownNode("poses"),
                        cooldownNode("skin"),
                        new WarmupKind("tp", 0).warmupNode(),
                        new WarmupKind("customcommand", 0).warmupNode());
        assertThat(problems).isEmpty();
    }

    @Test
    void everyQuotaFamilyTheCodeBuildsHasATierRow() throws ReflectiveOperationException {
        List<String> problems = new ArrayList<>();
        Set<String> checked = new TreeSet<>();
        for (Path file : MainSources.files()) {
            String source = MainSources.read(file);
            String name = file.getFileName().toString();
            Matcher family = QUOTA_FAMILY.matcher(source);
            while (family.find()) {
                Optional<Checked> held = heldTier(source, family.group(1).strip());
                if (held.isEmpty()) {
                    if (!PASS_THROUGH.contains(name)) {
                        problems.add(name + ": cannot tell which head " + family.group(1) + " names");
                    }
                    continue;
                }
                checked.add(held.get().node());
                if (!isListed(held.get())) {
                    problems.add(name + " checks " + held.get().node() + ", which no family row lists");
                }
            }
        }

        assertThat(checked)
                .contains(
                        tier(DestinationPermissions.HOME_LIMIT, "1"),
                        tier(EconomyPermissions.ECONOMY_SALARY_AMOUNT, "1"),
                        tier(EnforcementPermissions.maxDuration("sample"), "1"));
        assertThat(problems).isEmpty();
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

    /** Whether some family row, filled with this sample's values, is exactly the sample's node. */
    private static boolean isListed(Checked checked) {
        return families().stream().anyMatch(spec -> checked.isFilledFrom(spec.node()));
    }

    private static boolean isFactory(Method method) {
        int modifiers = method.getModifiers();
        return Modifier.isPublic(modifiers)
                && Modifier.isStatic(modifiers)
                && method.getReturnType() == String.class
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == String.class;
    }

    /**
     * The tier a player holding the head {@code argument} names would carry: the head with a sample number, and for
     * a factory head the sample it was called with. A bare constant is followed to its catalogue reference in the
     * same file. Empty when the head is handed in from elsewhere.
     */
    private static Optional<Checked> heldTier(String source, String argument) throws ReflectiveOperationException {
        String reference = argument;
        if (argument.matches("[A-Z][A-Z0-9_]*")) {
            Matcher constant = Pattern.compile("String\\s+" + argument + "\\s*=\\s*(\\w+Permissions\\.\\w+)\\s*;")
                    .matcher(source);
            if (!constant.find()) {
                return Optional.empty();
            }
            reference = constant.group(1);
        }
        Matcher head = CATALOG_HEAD.matcher(reference);
        if (!head.matches()) {
            return Optional.empty();
        }
        Class<?> catalog = Class.forName(PermissionSpec.class.getPackageName() + "." + head.group(1));
        if (head.group(3) == null) {
            String node = (String) catalog.getField(head.group(2)).get(null);
            return Optional.of(new Checked(tier(node, "1"), List.of("1")));
        }
        String node = (String) catalog.getMethod(head.group(2), String.class).invoke(null, "sample");
        return Optional.of(new Checked(tier(node, "1"), List.of("sample", "1")));
    }

    private static String cooldownNode(String feature) {
        return new CooldownKind(feature, 0, CooldownStartPhase.TELEPORT).cooldownNode();
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
