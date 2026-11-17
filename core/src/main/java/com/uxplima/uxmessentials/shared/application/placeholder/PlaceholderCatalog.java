package com.uxplima.uxmessentials.shared.application.placeholder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Every placeholder the plugin answers, in one list.
 *
 * <p>The resolver decides what a key renders; this decides which keys exist. A guard holds the two together in both
 * directions, so a key cannot be added to the resolver without becoming visible to operators, and a key cannot be
 * listed here without something answering it.
 */
public final class PlaceholderCatalog {

    /** The family that answers any other key about a named player. */
    private static final String OTHER_PLAYER = "p_";

    private static final List<PlaceholderSpec> ALL = Stream.of(
                    SharedPlaceholderKeys.all(),
                    DestinationPlaceholderKeys.all(),
                    EconomyPlaceholderKeys.all(),
                    SocialPlaceholderKeys.all(),
                    StatePlaceholderKeys.all(),
                    ContentPlaceholderKeys.all(),
                    WorldMechanicsPlaceholderKeys.all())
            .flatMap(List::stream)
            .toList();

    private PlaceholderCatalog() {}

    /** Every key, in the order the areas are written. */
    public static List<PlaceholderSpec> all() {
        return ALL;
    }

    /** The areas an operator can browse, the kernel first and the modules alphabetically after it. */
    public static List<String> areas() {
        List<String> areas = new ArrayList<>(
                ALL.stream().map(PlaceholderSpec::area).distinct().sorted().toList());
        if (areas.remove("shared")) {
            areas.addFirst("shared");
        }
        return List.copyOf(areas);
    }

    /** The keys of one area, alphabetically, or nothing when the area is not one of ours. */
    public static List<PlaceholderSpec> forArea(String area) {
        Objects.requireNonNull(area, "area");
        String wanted = area.toLowerCase(Locale.ROOT);
        return ALL.stream()
                .filter(spec -> spec.area().equals(wanted))
                .sorted(Comparator.comparing(PlaceholderSpec::key))
                .toList();
    }

    /**
     * The entry that answers {@code key}: the exact match first, then the family with the longest head that covers
     * it, so {@code kit_cost_starter} finds {@code kit_cost_<kit>} rather than a shorter neighbour.
     */
    public static Optional<PlaceholderSpec> find(String key) {
        Objects.requireNonNull(key, "key");
        String wanted = key.toLowerCase(Locale.ROOT);
        Optional<PlaceholderSpec> exact = ALL.stream()
                .filter(spec ->
                        spec.shape() == PlaceholderShape.FIXED && spec.key().equals(wanted))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        return ALL.stream()
                .filter(spec -> spec.shape() == PlaceholderShape.FAMILY && spec.covers(wanted))
                .max(Comparator.comparingInt(spec -> spec.head().length()));
    }

    /**
     * Whether the value of {@code key} is markup the operator wrote, parsed in our own lines rather than shown as words.
     * {@code p_<name>_<key>} answers for the key it reads about another player. An unknown key is not markup.
     */
    public static boolean markup(String key) {
        Objects.requireNonNull(key, "key");
        String wanted = key.toLowerCase(Locale.ROOT);
        if (wanted.startsWith(OTHER_PLAYER)) {
            String tail = wanted.substring(OTHER_PLAYER.length());
            for (int split = tail.indexOf('_'); split > 0; split = tail.indexOf('_', split + 1)) {
                Optional<PlaceholderSpec> read = find(tail.substring(split + 1));
                if (read.isPresent()) {
                    return read.get().markup();
                }
            }
            return false;
        }
        return find(wanted).map(PlaceholderSpec::markup).orElse(false);
    }
}
