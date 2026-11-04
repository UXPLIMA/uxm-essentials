package com.uxplima.uxmessentials.guard.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.BehaviourKey;
import com.uxplima.uxmessentials.guard.domain.Behaviours;
import com.uxplima.uxmessentials.guard.domain.Effect;
import com.uxplima.uxmessentials.guard.domain.GuardScope;
import com.uxplima.uxmessentials.guard.domain.RoleName;
import com.uxplima.uxmessentials.guard.domain.Rule;
import com.uxplima.uxmessentials.guard.domain.Target;
import com.uxplima.uxmessentials.guard.domain.Template;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed view of {@code modules/guard/config.conf}, read whole on start and on reload.
 *
 * <p>A rule line is {@code <action> <target> <allow|deny>}, the target being everything between the first and
 * the last word so a condition may hold spaces; a behaviour line is {@code <key> = <value>}. A line
 * that does not parse is reported through the {@code problems} consumer and skipped, never guessed at.
 *
 * @param enabled the module switch
 * @param serverScope this server's own scope
 * @param sharedSets the shared sets this server loads
 * @param newRegionScope the scope new regions get unless one is named
 * @param pollInterval how often to poll the store for peer changes when Redis is absent
 * @param denyChannel {@code actionbar} or {@code chat}
 * @param denyCooldown the gap between two denial messages to one player
 * @param bypassReminder how often a player with bypass on is reminded
 * @param displayMode {@code lines} or {@code particles}
 * @param displayDuration how long {@code /guard show} draws
 * @param displayRange how far edges are drawn
 * @param movementCheckTicks the Folia teleport watchdog period
 * @param alerts staff alert settings
 * @param denialLog denial log settings
 * @param backupsKept how many backup files are kept
 * @param extraRoles roles beyond the four built in
 * @param templates region templates by name
 * @param worlds per world defaults by world name; the {@code default} entry applies to worlds not listed
 * @param rgAlias also register {@code /rg}
 * @param claimDistance how far from a guard region claims are refused
 */
public record GuardConfig(
        boolean enabled,
        GuardScope serverScope,
        List<String> sharedSets,
        GuardScope newRegionScope,
        Duration pollInterval,
        String denyChannel,
        Duration denyCooldown,
        Duration bypassReminder,
        String displayMode,
        Duration displayDuration,
        int displayRange,
        int movementCheckTicks,
        Alerts alerts,
        DenialLog denialLog,
        int backupsKept,
        List<RoleName> extraRoles,
        Map<String, Template> templates,
        Map<String, WorldDefaults> worlds,
        boolean rgAlias,
        int claimDistance) {

    public GuardConfig {
        Objects.requireNonNull(serverScope, "serverScope");
        sharedSets = List.copyOf(sharedSets);
        Objects.requireNonNull(newRegionScope, "newRegionScope");
        extraRoles = List.copyOf(extraRoles);
        templates = Map.copyOf(templates);
        worlds = Map.copyOf(worlds);
    }

    /**
     * Staff alerts for repeated denied attempts.
     *
     * @param enabled on or off
     * @param threshold denials within the window that raise an alert
     * @param window the counting window
     * @param cooldown the gap between two alerts about one player
     * @param discord also send to the Discord bridge
     */
    public record Alerts(boolean enabled, int threshold, Duration window, Duration cooldown, boolean discord) {}

    /**
     * The denial log.
     *
     * @param enabled on or off
     * @param keepDays how long rows are kept
     */
    public record DenialLog(boolean enabled, int keepDays) {}

    /**
     * One world's defaults.
     *
     * @param highFrequency protect liquid flow, fire spread, growth and decay in this world
     * @param rules the default rules
     * @param behaviours the default behaviours
     */
    public record WorldDefaults(boolean highFrequency, List<Rule> rules, Behaviours behaviours) {
        public WorldDefaults {
            rules = List.copyOf(rules);
        }

        static final WorldDefaults NONE = new WorldDefaults(true, List.of(), Behaviours.empty());
    }

    /** The defaults for {@code world}: its own entry, else the {@code default} entry, else nothing. */
    public WorldDefaults world(String world) {
        WorldDefaults own = worlds.get(world);
        if (own != null) {
            return own;
        }
        return worlds.getOrDefault("default", WorldDefaults.NONE);
    }

    /** Every scope this server loads. */
    public List<GuardScope> loadedScopes() {
        List<GuardScope> scopes = new ArrayList<>();
        scopes.add(serverScope);
        for (String set : sharedSets) {
            scopes.add(GuardScope.set(set));
        }
        return List.copyOf(scopes);
    }

    /** Read the module config. {@code serverId} is the network's id for this server. */
    public static GuardConfig from(ConfigStore config, String serverId, Consumer<String> problems) {
        Objects.requireNonNull(config, "config");
        GuardScope server = GuardScope.server(serverId);
        List<String> sets = config.getStringList("network.shared-sets", List.of()).stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        String scopeName =
                config.getString("network.new-region-scope", "server").strip();
        GuardScope newScope =
                scopeName.equalsIgnoreCase("server") || !sets.contains(scopeName) ? server : GuardScope.set(scopeName);
        if (!scopeName.equalsIgnoreCase("server") && !sets.contains(scopeName)) {
            problems.accept("network.new-region-scope names a set this server does not load: " + scopeName);
        }
        String channel =
                config.getString("deny-message.channel", "actionbar").strip().toLowerCase(Locale.ROOT);
        if (!channel.equals("actionbar") && !channel.equals("chat")) {
            problems.accept("deny-message.channel must be actionbar or chat: " + channel);
            channel = "actionbar";
        }
        String mode = config.getString("display.mode", "lines").strip().toLowerCase(Locale.ROOT);
        if (!mode.equals("lines") && !mode.equals("particles")) {
            problems.accept("display.mode must be lines or particles: " + mode);
            mode = "lines";
        }
        List<RoleName> roles = new ArrayList<>();
        for (String raw : config.getStringList("roles.extra", List.of())) {
            try {
                RoleName role = RoleName.of(raw);
                if (!RoleName.BUILT_IN.contains(role)) {
                    roles.add(role);
                }
            } catch (IllegalArgumentException e) {
                problems.accept("roles.extra: not a role name: " + raw);
            }
        }
        Map<String, Template> templates = new LinkedHashMap<>();
        for (String name : config.getKeys("templates")) {
            String base = "templates." + name;
            templates.put(
                    name.toLowerCase(Locale.ROOT),
                    new Template(
                            name,
                            rules(config.getStringList(base + ".rules", List.of()), base + ".rules", problems),
                            behaviours(
                                    config.getStringList(base + ".behaviours", List.of()),
                                    base + ".behaviours",
                                    problems)));
        }
        Map<String, WorldDefaults> worlds = new LinkedHashMap<>();
        for (String name : config.getKeys("worlds")) {
            String base = "worlds." + name;
            worlds.put(
                    name,
                    new WorldDefaults(
                            config.getBoolean(base + ".high-frequency", true),
                            rules(config.getStringList(base + ".rules", List.of()), base + ".rules", problems),
                            behaviours(
                                    config.getStringList(base + ".behaviours", List.of()),
                                    base + ".behaviours",
                                    problems)));
        }
        return new GuardConfig(
                config.getBoolean("enabled", true),
                server,
                sets,
                newScope,
                Duration.ofSeconds(Math.max(2, config.getInt("network.poll-seconds", 10))),
                channel,
                Duration.ofMillis(Math.max(0, config.getInt("deny-message.cooldown-ms", 1500))),
                Duration.ofSeconds(Math.max(5, config.getInt("bypass.reminder-seconds", 30))),
                mode,
                Duration.ofSeconds(Math.max(1, config.getInt("display.seconds", 20))),
                Math.max(8, config.getInt("display.range", 64)),
                Math.max(1, config.getInt("movement.check-ticks", 10)),
                new Alerts(
                        config.getBoolean("alerts.enabled", true),
                        Math.max(1, config.getInt("alerts.threshold", 8)),
                        Duration.ofSeconds(Math.max(1, config.getInt("alerts.window-seconds", 20))),
                        Duration.ofSeconds(Math.max(1, config.getInt("alerts.cooldown-seconds", 60))),
                        config.getBoolean("alerts.discord", true)),
                new DenialLog(config.getBoolean("log.enabled", false), Math.max(1, config.getInt("log.keep-days", 14))),
                Math.max(1, config.getInt("backups.keep", 10)),
                roles,
                templates,
                worlds,
                config.getBoolean("commands.rg-alias", false),
                Math.max(0, config.getInt("claims.guard-distance", 16)));
    }

    /** Parse rule lines: {@code <action> <target> <allow|deny>}. */
    public static List<Rule> rules(List<String> lines, String where, Consumer<String> problems) {
        List<Rule> rules = new ArrayList<>();
        for (String line : lines) {
            parseRule(line).ifPresentOrElse(rules::add, () -> problems.accept(where + ": not a rule: " + line));
        }
        return rules;
    }

    /** Parse one rule line. */
    public static Optional<Rule> parseRule(String line) {
        String text = line.strip();
        int first = text.indexOf(' ');
        int last = text.lastIndexOf(' ');
        if (first < 0 || last <= first) {
            return Optional.empty();
        }
        try {
            ActionId action = ActionId.of(text.substring(0, first));
            Optional<Target> target = Target.parse(text.substring(first + 1, last));
            Optional<Effect> effect = Effect.parse(text.substring(last + 1));
            if (target.isEmpty() || effect.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Rule(action, target.get(), effect.get()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Parse behaviour lines: {@code <key> = <value>}. */
    public static Behaviours behaviours(List<String> lines, String where, Consumer<String> problems) {
        Map<BehaviourKey, String> values = new EnumMap<>(BehaviourKey.class);
        for (String line : lines) {
            int eq = line.indexOf('=');
            Optional<BehaviourKey> key = eq < 0 ? Optional.empty() : BehaviourKey.parse(line.substring(0, eq));
            Optional<String> value = key.flatMap(k -> k.normalise(line.substring(eq + 1)));
            if (key.isEmpty() || value.isEmpty()) {
                problems.accept(where + ": not a behaviour: " + line);
                continue;
            }
            values.put(key.get(), value.get());
        }
        return Behaviours.of(values);
    }
}
