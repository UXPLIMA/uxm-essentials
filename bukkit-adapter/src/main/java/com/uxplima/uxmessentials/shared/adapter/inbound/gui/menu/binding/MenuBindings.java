package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;

/**
 * The single entry point a feature uses to give a menu spec its behaviour. Features call {@code action} /
 * {@code condition} / {@code placeholder} / {@code list} once at wiring time to register a handler under an id;
 * the engine later resolves a spec's refs back to those handlers through the matching getter. {@link #validate}
 * turns a spec that names an unregistered id into a loud startup failure instead of a broken menu a player meets.
 */
public final class MenuBindings {

    /** Matches a {@code %token%} placeholder; the captured group is the bare id a feature registers. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_]+)%");

    private final ActionRegistry actions = new ActionRegistry();

    private final ConditionRegistry conditions = new ConditionRegistry();

    private final PlaceholderRegistry placeholders = new PlaceholderRegistry();

    private final ListSourceRegistry lists = new ListSourceRegistry();

    public void action(String id, Consumer<MenuActionContext> handler) {
        actions.register(id, handler);
    }

    public void condition(String id, BiPredicate<MenuContext, Map<String, String>> handler) {
        conditions.register(id, handler);
    }

    public void placeholder(String id, Function<MenuContext, String> handler) {
        placeholders.register(id, handler);
    }

    public void list(String id, Function<MenuContext, List<?>> handler) {
        lists.register(id, handler);
    }

    public Optional<Consumer<MenuActionContext>> action(String id) {
        return actions.get(id);
    }

    public Optional<BiPredicate<MenuContext, Map<String, String>>> condition(String id) {
        return conditions.get(id);
    }

    public Optional<Function<MenuContext, String>> placeholder(String id) {
        return placeholders.get(id);
    }

    public Optional<Function<MenuContext, List<?>>> list(String id) {
        return lists.get(id);
    }

    /**
     * The four backing registries, handed to the renderer and click listener so they resolve refs against the
     * very instances this façade writes to. A feature registering a handler after wiring is therefore visible to
     * an already-built engine — there is one registry per kind, not a copy per consumer.
     */
    public ActionRegistry actions() {
        return actions;
    }

    public ConditionRegistry conditions() {
        return conditions;
    }

    public PlaceholderRegistry placeholders() {
        return placeholders;
    }

    public ListSourceRegistry lists() {
        return lists;
    }

    /**
     * Every ref id a spec names that no registry knows, de-duplicated. An empty list means the specs are fully
     * wired; anything returned is a missing binding the operator must fix before the menu can open cleanly.
     */
    public List<String> validate(Collection<MenuSpec> specs) {
        Objects.requireNonNull(specs, "specs");
        Set<String> missing = new LinkedHashSet<>();
        for (MenuSpec spec : specs) {
            collectMissing(spec, missing);
        }
        return List.copyOf(missing);
    }

    private void collectMissing(MenuSpec spec, Set<String> missing) {
        addMissing(spec.openRequirement(), conditions::has, missing);
        addMissing(spec.openActions(), actions::has, missing);
        addMissing(spec.closeActions(), actions::has, missing);
        for (String id : extractPlaceholders(spec.title())) {
            if (!placeholders.has(id)) {
                missing.add(id);
            }
        }
        for (MenuItemSpec item : spec.items().values()) {
            collectItemMissing(item, missing);
        }
    }

    private void collectItemMissing(MenuItemSpec item, Set<String> missing) {
        addMissing(item.view(), conditions::has, missing);
        item.click().conditions().values().forEach(refs -> addMissing(refs, conditions::has, missing));
        item.click().actions().values().forEach(refs -> addMissing(refs, actions::has, missing));
        collectTextPlaceholders(item, missing);
        item.list().ifPresent(list -> {
            if (!lists.has(list.source().id())) {
                missing.add(list.source().id());
            }
            collectItemMissing(list.template(), missing);
        });
    }

    private void collectTextPlaceholders(MenuItemSpec item, Set<String> missing) {
        List<String> texts = new ArrayList<>(item.lore());
        texts.add(item.material());
        texts.add(item.name());
        for (String text : texts) {
            for (String id : extractPlaceholders(text)) {
                if (!placeholders.has(id)) {
                    missing.add(id);
                }
            }
        }
    }

    /**
     * Collect the refs in {@code refs} whose id no registry knows. A ref is first resolved against {@code known}
     * (the matching registry's {@code has}), so a valued token written {@code has-money:100} counts as known when its
     * head {@code has-money} is registered — the same registry-aware split the runtime does at dispatch/render time.
     * The original written token is reported when it is still unknown, so an operator sees exactly what they typed.
     */
    private void addMissing(List<Ref> refs, Predicate<String> known, Set<String> missing) {
        for (Ref ref : refs) {
            if (!known.test(ref.resolve(known).id())) {
                missing.add(ref.id());
            }
        }
    }

    /** The bare ids of every {@code %token%} placeholder in {@code text}, in order of appearance. */
    private static List<String> extractPlaceholders(String text) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }
}
