package com.uxplima.uxmessentials.velocity.commandcontrol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.uxplima.uxmessentials.commandcontrol.domain.HidePolicy;
import com.uxplima.uxmessentials.commandcontrol.domain.PlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleMode;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import org.junit.jupiter.api.Test;

/**
 * Verifies the proxy command-tree filter over a hand-built Brigadier tree: a non-permitted viewer loses the
 * hidden proxy commands ({@code /server}, {@code /plugins}) and the blacklisted one ({@code /glist}), keeping
 * only the allowed root, and a bypass/view holder keeps the whole tree. The namespaced form of a denied
 * command is removed too when the namespace-bypass block is on.
 */
class ProxyCommandTreeFilterTest {

    private static final String BYPASS = "uxmessentials.commandcontrol.bypass";
    private static final String VIEW = "uxmessentials.commandcontrol.viewproxycommands";

    private static RootCommandNode<Object> tree(String... names) {
        RootCommandNode<Object> root = new RootCommandNode<>();
        for (String name : names) {
            root.addChild(LiteralArgumentBuilder.<Object>literal(name).build());
        }
        return root;
    }

    private static List<String> names(RootCommandNode<Object> root) {
        return root.getChildren().stream().map(CommandNode::getName).toList();
    }

    private static PlayerFacts facts(String... heldPermissions) {
        Set<String> held = Set.of(heldPermissions);
        return new PlayerFacts() {
            @Override
            public Optional<String> group() {
                return Optional.empty();
            }

            @Override
            public boolean hasPermission(String node) {
                return held.contains(node);
            }
        };
    }

    private static ProxyCommandTreeFilter filter() {
        RuleSet rules = RuleSet.of(RuleMode.BLACKLIST, List.of("glist"), Map.of(), BYPASS);
        HidePolicy hide = HidePolicy.of(true, List.of("server", "plugins"), VIEW);
        return new ProxyCommandTreeFilter(rules, hide, true, true);
    }

    @Test
    void removesHiddenAndDeniedNodesForANonPermittedViewer() {
        RootCommandNode<Object> root = tree("server", "glist", "home", "plugins");

        filter().filter(root, facts());

        assertThat(names(root)).containsExactly("home");
    }

    @Test
    void keepsEveryNodeForABypassAndViewHolder() {
        RootCommandNode<Object> root = tree("server", "glist", "home", "plugins");

        filter().filter(root, facts(BYPASS, VIEW));

        assertThat(names(root)).containsExactlyInAnyOrder("server", "glist", "home", "plugins");
    }

    @Test
    void removesTheNamespacedFormOfADeniedCommand() {
        RootCommandNode<Object> root = tree("velocity:glist", "home");

        filter().filter(root, facts());

        assertThat(names(root)).containsExactly("home");
    }

    @Test
    void keepsEverythingWhenTabFilterOffAndHideInactive() {
        RuleSet blacklist = RuleSet.of(RuleMode.BLACKLIST, List.of("glist"), Map.of(), BYPASS);
        HidePolicy hideOff = HidePolicy.of(false, List.of(), VIEW);
        ProxyCommandTreeFilter filter = new ProxyCommandTreeFilter(blacklist, hideOff, false, true);
        RootCommandNode<Object> root = tree("glist", "home");

        filter.filter(root, facts());

        assertThat(names(root)).containsExactlyInAnyOrder("glist", "home");
    }
}
