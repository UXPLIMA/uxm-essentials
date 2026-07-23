package com.uxplima.uxmessentials.velocity.commandcontrol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.uxplima.uxmessentials.commandcontrol.domain.HidePolicy;
import com.uxplima.uxmessentials.commandcontrol.domain.NamespaceBypassRule;
import com.uxplima.uxmessentials.commandcontrol.domain.PlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;

/**
 * The proxy equivalent of the backend's Brigadier {@code .requires()} pruning: given the command tree the
 * proxy is about to advertise to a client ({@code PlayerAvailableCommandsEvent}), it removes every root
 * command node the viewer should not see, so a non-permitted player never sees {@code /server},
 * {@code /glist}, plugin commands, and so on in tab-completion or the client command list.
 *
 * <p>The keep/remove decision is delegated to the pure {@code :core} domain: a node is removed when the
 * world-free {@link HidePolicy} hides it, or when the {@link RuleSet} denies it (whitelist/blacklist) and
 * the tab filter is on. When {@code block-namespace-bypass} is on, a namespaced node ({@code velocity:glist})
 * whose bare form is denied is removed too, closing the same escape the backend blocks. The filter never
 * sees a {@code .bypass} holder: the listener short-circuits one before calling in, so the bypass keeps the
 * full tree.
 *
 * <p>Child names are collected first and removed in a second pass so the tree is not mutated while it is
 * being iterated. The filter is a plain function over a {@link RootCommandNode}, so it unit-tests against a
 * hand-built command tree with no live proxy.
 */
public final class ProxyCommandTreeFilter {

    private final RuleSet rules;
    private final HidePolicy hidePolicy;
    private final boolean tabCompletionEnabled;
    private final boolean blockNamespaceBypass;

    public ProxyCommandTreeFilter(
            RuleSet rules, HidePolicy hidePolicy, boolean tabCompletionEnabled, boolean blockNamespaceBypass) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.hidePolicy = Objects.requireNonNull(hidePolicy, "hidePolicy");
        this.tabCompletionEnabled = tabCompletionEnabled;
        this.blockNamespaceBypass = blockNamespaceBypass;
    }

    /** Remove from {@code root} every child command node {@code facts} should not see. */
    public void filter(RootCommandNode<?> root, PlayerFacts facts) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(facts, "facts");
        List<String> toRemove = new ArrayList<>();
        for (CommandNode<?> child : root.getChildren()) {
            String name = child.getName();
            if (name != null && shouldRemove(name, facts)) {
                toRemove.add(name);
            }
        }
        for (String name : toRemove) {
            root.removeChildByName(name);
        }
    }

    /** True when a root command label is hidden by the hide policy or denied by an active rule set. */
    public boolean shouldRemove(String commandRoot, PlayerFacts facts) {
        Objects.requireNonNull(commandRoot, "commandRoot");
        Objects.requireNonNull(facts, "facts");
        if (hidePolicy.shouldHide(commandRoot, facts)) {
            return true;
        }
        if (!tabActive()) {
            return false;
        }
        if (rules.decide(commandRoot, facts) == RuleSet.Decision.DENY) {
            return true;
        }
        if (!blockNamespaceBypass) {
            return false;
        }
        return NamespaceBypassRule.bareRoot(commandRoot)
                .map(bare -> rules.decide(bare, facts) == RuleSet.Decision.DENY)
                .orElse(false);
    }

    /** True when the tab filter can remove anything: switched on and the rule set is not inert. */
    private boolean tabActive() {
        return tabCompletionEnabled && !rules.isInert();
    }
}
