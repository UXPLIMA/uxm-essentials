package com.uxplima.uxmessentials.shared.application.permission;

import java.util.List;
import java.util.stream.Stream;

/**
 * The permission table for the cross-cutting surfaces no single module owns. Data, not logic: one row per node, read
 * by {@link PermissionCatalog} and through it by the server registration, the in-game listing and its export.
 *
 * <p>Every node is also a constant here, or a factory for a family named at runtime, and the code checks it through
 * that constant, so the code holds no second copy of the string for a rename to miss.
 */
public final class SharedPermissions {

    public static final String ADMIN = "uxmessentials.admin";
    public static final String ADMIN_BACKUP = "uxmessentials.admin.backup";
    public static final String ADMIN_IMPORT = "uxmessentials.admin.import";
    public static final String ADMIN_PERMISSIONS = "uxmessentials.admin.permissions";
    public static final String ADMIN_PLACEHOLDERS = "uxmessentials.admin.placeholders";
    public static final String ADMIN_RELOAD = "uxmessentials.admin.reload";
    public static final String ADMIN_DOCTOR_REPAIR = "uxmessentials.admin.doctor.repair";
    public static final String GUI = "uxmessentials.gui";
    public static final String HELP = "uxmessentials.help";
    public static final String LANG_USE = "uxmessentials.lang.use";
    public static final String LANG_OTHERS = "uxmessentials.lang.others";
    public static final String REST_ADMIN = "uxmessentials.rest.admin";
    public static final String UPDATE_NOTIFY = "uxmessentials.update.notify";

    /** The space every node of the plugin lives in, separator included. */
    public static final String NAMESPACE = "uxmessentials.";

    /** The head every exemption starts with, so a permission plugin lists them together. */
    static final String BYPASS = "uxmessentials.bypass.";

    private static final String COOLDOWN_PREFIX = "uxmessentials.cooldown.";
    private static final String COOLDOWN_BYPASS_PREFIX = "uxmessentials.bypass.cooldown.";
    private static final String LABEL_COOLDOWN_BYPASS_PREFIX = "uxmessentials.bypass.cooldown.label.";
    private static final String WARMUP_BYPASS_PREFIX = "uxmessentials.bypass.warmup.";
    private static final String MODULE_PREFIX = "uxmessentials.module.";

    /** The head of the cooldown tiers for one label a menu or custom command names; the seconds are appended. */
    public static String cooldown(String label) {
        return COOLDOWN_PREFIX + label;
    }

    /**
     * The node that skips the cooldown of one label a menu or custom command names. Labels are chosen by operators,
     * so they sit in their own {@code label.} branch: a menu label called {@code kit} must never also lift the kit
     * cooldown.
     */
    public static String cooldownBypass(String label) {
        return LABEL_COOLDOWN_BYPASS_PREFIX + label;
    }

    /** The head of the cooldown tiers a feature resolves through its own segment, as {@code tp} does. */
    public static String featureCooldown(String feature) {
        return NAMESPACE + feature + ".cooldown";
    }

    /** The node that skips the cooldown a feature resolves through its own segment. */
    public static String featureCooldownBypass(String feature) {
        return COOLDOWN_BYPASS_PREFIX + feature;
    }

    /** The head of the warmup tiers a feature resolves through its own segment, as {@code tp} does. */
    public static String featureWarmup(String feature) {
        return NAMESPACE + feature + ".warmup";
    }

    /** The node that starts a feature's warmup-gated action at once. */
    public static String featureWarmupBypass(String feature) {
        return WARMUP_BYPASS_PREFIX + feature;
    }

    /** The operator node that reloads and inspects one module. */
    public static String module(String id) {
        return MODULE_PREFIX + id;
    }

    private SharedPermissions() {}

    static List<PermissionSpec> all() {
        return Stream.of(shared()).flatMap(List::stream).toList();
    }

    private static List<PermissionSpec> shared() {
        return List.of(
                PermissionSpec.shared(ADMIN, "Access to the /uxmess administration root.", PermissionDefault.OP),
                PermissionSpec.shared(
                        ADMIN_BACKUP, "/backup to snapshot the plugin data directory on demand.", PermissionDefault.OP),
                PermissionSpec.shared(
                        ADMIN_IMPORT,
                        "/uxmess import <plugin>: run the one-shot data import from another essentials plugin.",
                        PermissionDefault.OP),
                PermissionSpec.shared(
                        ADMIN_PERMISSIONS,
                        "/uxmess permissions [area] [page] and /uxmess permissions export: read the permission catalogue in game or write it to a file.",
                        PermissionDefault.OP),
                PermissionSpec.shared(
                        ADMIN_PLACEHOLDERS,
                        "/uxmess placeholders [area] [page] and /uxmess placeholders export: read the placeholder catalogue in game or write it to a file.",
                        PermissionDefault.OP),
                PermissionSpec.shared(ADMIN_RELOAD, "Reload all modules via /uxmess reload.", PermissionDefault.OP),
                PermissionSpec.shared(
                        ADMIN_DOCTOR_REPAIR,
                        "/uxmess doctor repair confirm: transactionally remove safe, unambiguous orphan rows.",
                        PermissionDefault.OP),
                PermissionSpec.sharedFamily(
                        cooldown("<label>") + ".<seconds>",
                        "The wait between uses of one label a menu or custom command names, in seconds; a held tier sets the wait, the shortest held wins and 0 removes it; with none held the configured wait applies. A built-in feature's wait is its own <feature>.cooldown.<seconds> node.",
                        PermissionDefault.TRUE,
                        PermissionShape.TIER),
                PermissionSpec.sharedFamily(
                        featureCooldownBypass("<feature>"),
                        "Skip the cooldown on one rate-limited feature entirely: tp, rtp, kit, poses or skin.",
                        PermissionDefault.OP,
                        PermissionShape.LABEL),
                PermissionSpec.sharedFamily(
                        cooldownBypass("<label>"),
                        "Skip the cooldown of one label a menu or custom command names, such as customcommand.<id>.",
                        PermissionDefault.OP,
                        PermissionShape.LABEL),
                PermissionSpec.sharedFamily(
                        featureWarmupBypass("<feature>"),
                        "Start one warmup-gated feature at once, immune to move-cancel: tp or customcommand.",
                        PermissionDefault.OP,
                        PermissionShape.LABEL),
                PermissionSpec.shared(GUI, "/uxmess gui to open the module management hub.", PermissionDefault.OP),
                PermissionSpec.shared(HELP, "/help to list the commands you can use.", PermissionDefault.TRUE),
                PermissionSpec.shared(
                        LANG_USE, "/lang to set or clear your personal language override.", PermissionDefault.TRUE),
                PermissionSpec.shared(
                        LANG_OTHERS,
                        "/lang set <player> <language> and /lang reset <player>: set or clear another player's language, online or away.",
                        PermissionDefault.OP),
                PermissionSpec.shared(
                        REST_ADMIN,
                        "Checked by the optional uxmEssentials-rest add-on: /uxmapi token create|list|revoke and /uxmapi status to manage API tokens.",
                        PermissionDefault.OP),
                PermissionSpec.shared(
                        UPDATE_NOTIFY,
                        "Receive the join-time notice when a newer plugin version is available.",
                        PermissionDefault.OP));
    }
}
