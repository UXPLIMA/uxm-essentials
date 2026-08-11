package com.uxplima.uxmessentials.commandcontrol.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmCommandCheck;
import com.uxplima.uxmessentials.api.view.UxmCommandRule;
import com.uxplima.uxmessentials.commandcontrol.adapter.CommandControlWiring;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSource;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleMode;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.commandcontrol.domain.WorldRuleSets;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The published command check: it answers what the gate would do, it names the rule and whose list it came from,
 * and it closes the namespace bypass exactly when the gate does.
 */
class CommandControlQueriesTest {

    private static final String BYPASS = CommandControlWiring.BYPASS_PERMISSION;

    private ServerMock server;
    private PlayerMock alice;
    private PlayerRef who;
    private QueryDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        alice = server.addPlayer("Alice");
        who = new PlayerRef(alice.getUniqueId(), alice.getName());
        scheduler = new QueryDoubles.InlineScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aBlacklistedCommandIsBlockedAndSaysWhy() {
        CommandControlQueries queries = queries(blacklist("op"), false, PlayerGroupSource.empty());

        UxmCommandCheck check = queries.check(who.uuid(), "/op someone").join().orElseThrow();

        assertThat(check.command()).isEqualTo("op");
        assertThat(check.allowed()).isFalse();
        assertThat(check.blocked()).isTrue();
        assertThat(check.rule()).isEqualTo(UxmCommandRule.BLACKLISTED);
        assertThat(check.group()).isEmpty();
        assertThat(check.world()).isEmpty();
        assertThat(scheduler.entityCalls()).isOne();
    }

    @Test
    void anythingElseOnABlacklistRunsAndSaysThatToo() {
        CommandControlQueries queries = queries(blacklist("op"), false, PlayerGroupSource.empty());

        UxmCommandCheck check = queries.check(who.uuid(), "home").join().orElseThrow();

        assertThat(check.allowed()).isTrue();
        assertThat(check.rule()).isEqualTo(UxmCommandRule.NOT_BLACKLISTED);
    }

    @Test
    void aWhitelistNamesTheOtherTwoRules() {
        RuleSet rules = RuleSet.of(RuleMode.WHITELIST, List.of("home"), Map.of(), BYPASS);
        CommandControlQueries queries = queries(WorldRuleSets.ofBase(rules), false, PlayerGroupSource.empty());

        assertThat(queries.check(who.uuid(), "home").join().orElseThrow().rule())
                .isEqualTo(UxmCommandRule.WHITELISTED);
        assertThat(queries.check(who.uuid(), "op").join().orElseThrow().rule())
                .isEqualTo(UxmCommandRule.NOT_WHITELISTED);
    }

    @Test
    void aBypassHolderIsAllowedWithoutAListBeingNamed() {
        alice.addAttachment(MockBukkit.createMockPlugin("uxmEssentials"), BYPASS, true);
        CommandControlQueries queries = queries(blacklist("op"), false, PlayerGroupSource.empty());

        UxmCommandCheck check = queries.check(who.uuid(), "op").join().orElseThrow();

        assertThat(check.allowed()).isTrue();
        assertThat(check.rule()).isEqualTo(UxmCommandRule.BYPASS);
        assertThat(check.group()).isEmpty();
    }

    @Test
    void theGroupWhoseListDecidedIsNamed() {
        RuleSet rules = RuleSet.of(RuleMode.BLACKLIST, List.of("op"), Map.of("staff", List.of("stop")), BYPASS);
        CommandControlQueries queries = queries(WorldRuleSets.ofBase(rules), false, player -> Optional.of("staff"));

        // The staff list denies /stop and says nothing about /op, so staff may run /op and everybody else may not.
        assertThat(queries.check(who.uuid(), "stop").join().orElseThrow().group())
                .contains("staff");
        assertThat(queries.check(who.uuid(), "op").join().orElseThrow().allowed())
                .isTrue();
    }

    @Test
    void theWorldIsNamedOnlyWhenItsOwnRulesDecided() {
        RuleSet base = blacklistRules("fly");
        RuleSet creative = blacklistRules();
        WorldRuleSets worlds = WorldRuleSets.of(base, Map.of(alice.getWorld().getName(), creative));

        assertThat(queries(worlds, false, PlayerGroupSource.empty())
                        .check(who.uuid(), "fly")
                        .join()
                        .orElseThrow()
                        .world())
                .contains(alice.getWorld().getName());
        assertThat(queries(WorldRuleSets.ofBase(base), false, PlayerGroupSource.empty())
                        .check(who.uuid(), "fly")
                        .join()
                        .orElseThrow()
                        .world())
                .isEmpty();
    }

    @Test
    void theNamespacedFormIsAnsweredAboutTheBareCommandWhenThatBypassIsClosed() {
        UxmCommandCheck closed = queries(blacklist("op"), true, PlayerGroupSource.empty())
                .check(who.uuid(), "/minecraft:op someone")
                .join()
                .orElseThrow();

        assertThat(closed.command()).isEqualTo("op");
        assertThat(closed.allowed()).isFalse();

        UxmCommandCheck open = queries(blacklist("op"), false, PlayerGroupSource.empty())
                .check(who.uuid(), "/minecraft:op someone")
                .join()
                .orElseThrow();

        // With the bypass left open the gate lets the namespaced form through, and so does the answer.
        assertThat(open.command()).isEqualTo("minecraft:op");
        assertThat(open.allowed()).isTrue();
    }

    @Test
    void aPlayerWhoIsNotHereIsAnEmptyAnswerRatherThanAGuess() {
        CommandControlQueries queries = queries(blacklist("op"), false, PlayerGroupSource.empty());

        assertThat(queries.check(UUID.randomUUID(), "op").join()).isEmpty();
        assertThat(queries.isBlocked(UUID.randomUUID(), "op").join()).isFalse();
    }

    @Test
    void isBlockedIsTheCheckWithEverythingButTheAnswerDropped() {
        CommandControlQueries queries = queries(blacklist("op"), false, PlayerGroupSource.empty());

        assertThat(queries.isBlocked(who.uuid(), "op").join()).isTrue();
        assertThat(queries.isBlocked(who.uuid(), "home").join()).isFalse();
    }

    private CommandControlQueries queries(WorldRuleSets rules, boolean blockNamespace, PlayerGroupSource groups) {
        return new CommandControlQueries(
                rules, blockNamespace, groups, new QueryDoubles.MapLookup().with(who), scheduler);
    }

    private static WorldRuleSets blacklist(String... denied) {
        return WorldRuleSets.ofBase(blacklistRules(denied));
    }

    private static RuleSet blacklistRules(String... denied) {
        return RuleSet.of(RuleMode.BLACKLIST, List.of(denied), Map.of(), BYPASS);
    }
}
