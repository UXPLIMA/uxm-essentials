package com.uxplima.uxmessentials.shared.application.permission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Answers a permission check with the catalogue's default where nobody set the node.
 *
 * <p>The server only knows the defaults of the nodes it was told about, and only the fixed nodes can be told: a
 * family such as {@code uxmessentials.warp.use.<warp>} is completed at runtime from a name the operator chose. The
 * server answers a node it does not know with its own default, operators only, so without this a family the
 * catalogue declares open to everyone would be closed to everyone but operators.
 *
 * <p>The rule, for one node:
 *
 * <ol>
 *   <li>A node the holder has set, granted or denied, is answered by that setting. A permission plugin that resolves
 *       wildcards reports the children of a granted or negated {@code .*} as set, so its answer stands.
 *   <li>A node that is not a member of a name family ({@link PermissionShape#LABEL}) is the server's to answer: a
 *       fixed node was registered with its default, and a node the catalogue does not know keeps the server's rule.
 *   <li>A wildcard above the node that the holder has set, the nearest first, decides it. This keeps a {@code .*}
 *       grant or negation meaning what it says on a server whose permission plugin does not resolve wildcards.
 *   <li>Otherwise the family's default decides, read the way the server reads a registered default.
 * </ol>
 *
 * <p>Number families ({@link PermissionShape#QUOTA}, {@link PermissionShape#TIER}) are left to the server. Their
 * default says the tier system applies to everyone, not that everyone holds every tier; they are read by listing the
 * nodes a player holds, and a default that answered yes for any probed number would hand out every tier.
 *
 * <p>Two families can share one shape. {@code uxmessentials.kit.cooldown.<seconds>} (a tier) and
 * {@code uxmessentials.kit.cooldown.<group>} (a name, default {@code false}) are both a word after the same prefix, so
 * a check of {@code uxmessentials.kit.cooldown.0} is answered as a group nobody was given. That is harmless: no code
 * asks for a tier node by name, the tier is read by {@code resolveQuota} from the nodes a player actually holds, and a
 * tier node somebody granted is set and answered by its setting before any default is looked at.
 *
 * <p>The catalogue is fixed at class load, so the lookup is built once: the fixed nodes as a set, and each name family
 * filed under its prefix up to the last dot before the placeholder. A check walks the dots of the node from the
 * right, which finds the most specific family first and costs a few map lookups.
 */
public final class CatalogDefaults {

    private static final String WILDCARD = "*";

    private static final Set<String> FIXED = PermissionCatalog.all().stream()
            .filter(PermissionSpec::registrable)
            .map(PermissionSpec::node)
            .collect(Collectors.toUnmodifiableSet());

    private static final Map<String, List<Shape>> LABELS = index();

    private CatalogDefaults() {}

    /** Whether {@code holder} holds {@code node}, by the rule in the class description. */
    public static boolean holds(PermissionHolder holder, String node) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(node, "node");
        // The family lookup comes first because most checks are for fixed or foreign nodes, which the server answers
        // whole, setting and default together, in one call.
        Optional<PermissionDefault> fallback = familyDefault(node);
        if (fallback.isEmpty() || holder.isPermissionSet(node)) {
            return holder.hasPermission(node);
        }
        for (int dot = node.lastIndexOf('.'); dot > 0; dot = node.lastIndexOf('.', dot - 1)) {
            String wildcard = node.substring(0, dot + 1) + WILDCARD;
            if (holder.isPermissionSet(wildcard)) {
                return holder.hasPermission(wildcard);
            }
        }
        if (holder.isPermissionSet(WILDCARD)) {
            return holder.hasPermission(WILDCARD);
        }
        return switch (fallback.get()) {
            case TRUE -> true;
            case FALSE -> false;
            case OP -> holder.isOp();
            case NOT_OP -> !holder.isOp();
        };
    }

    /**
     * The default of the name family {@code node} belongs to, or empty when it is a fixed node, a member of a number
     * family or a node the catalogue does not know.
     */
    public static Optional<PermissionDefault> familyDefault(String node) {
        Objects.requireNonNull(node, "node");
        if (!node.startsWith(SharedPermissions.NAMESPACE) || FIXED.contains(node)) {
            return Optional.empty();
        }
        for (int dot = node.lastIndexOf('.'); dot > 0; dot = node.lastIndexOf('.', dot - 1)) {
            List<Shape> shapes = LABELS.get(node.substring(0, dot + 1));
            if (shapes == null) {
                continue;
            }
            for (Shape shape : shapes) {
                if (shape.matches(node)) {
                    return Optional.of(shape.fallback());
                }
            }
        }
        return Optional.empty();
    }

    private static Map<String, List<Shape>> index() {
        Map<String, List<Shape>> byAnchor = new HashMap<>();
        for (PermissionSpec spec : PermissionCatalog.all()) {
            if (spec.shape() != PermissionShape.LABEL) {
                continue;
            }
            Shape shape = Shape.of(spec);
            String anchor = shape.prefix().substring(0, shape.prefix().lastIndexOf('.') + 1);
            byAnchor.computeIfAbsent(anchor, unused -> new ArrayList<>()).add(shape);
        }
        Map<String, List<Shape>> frozen = new HashMap<>();
        // Under one anchor the longer prefix is the narrower family, so it is tried first.
        byAnchor.forEach((anchor, shapes) -> frozen.put(
                anchor,
                shapes.stream()
                        .sorted(Comparator.comparingInt(
                                        (Shape shape) -> shape.prefix().length())
                                .reversed())
                        .toList()));
        return Map.copyOf(frozen);
    }

    /**
     * One name family as a match: the text before its placeholder, the text after it, and the default. The name
     * filling the placeholder must be at least one character, so the family's head is not mistaken for a member.
     */
    private record Shape(String prefix, String suffix, PermissionDefault fallback) {

        static Shape of(PermissionSpec spec) {
            String node = spec.node();
            int open = node.indexOf('<');
            int close = node.indexOf('>', open);
            return new Shape(node.substring(0, open), node.substring(close + 1), spec.fallback());
        }

        boolean matches(String node) {
            return node.length() > prefix.length() + suffix.length()
                    && node.startsWith(prefix)
                    && node.endsWith(suffix);
        }
    }
}
