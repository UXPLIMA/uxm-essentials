package com.uxplima.uxmessentials.kits.domain;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A mutable carrier used only to express single-field copies of an immutable {@link KitDefinition}; it never
 * escapes {@link KitDefinition}'s {@code copy} helper. Holding every component as a plain field lets a
 * {@code with<Field>} method change one of them and rebuild the record without re-listing the thirty-odd
 * unchanged components at each call site — extracted to its own file so the record itself stays focused on
 * its public contract rather than its copy plumbing.
 */
final class KitDefinitionFields {

    KitId id;
    List<KitItem> items;
    Duration cooldown;
    boolean oneTime;
    boolean permission;
    KitCost cost;
    Optional<String> displayName;
    Optional<String> displayMaterial;
    List<String> displayLore;
    List<String> commands;
    Optional<String> sound;
    Optional<String> particles;
    boolean firstJoin;
    boolean autoEquip;
    Optional<String> categoryId;
    java.math.BigDecimal claimMoney;
    String claimMoneyCurrency;
    Map<String, Duration> permissionCooldowns;
    int priority;
    Optional<String> noPermissionMaterial;
    Optional<String> noPermissionName;
    List<String> noPermissionLore;
    Optional<String> cooldownMaterial;
    Optional<String> cooldownName;
    List<String> cooldownLore;
    Optional<String> claimedMaterial;
    Optional<String> claimedName;
    List<String> claimedLore;
    Optional<String> unaffordableMaterial;
    Optional<String> unaffordableName;
    List<String> unaffordableLore;
    Optional<String> customPermission;
    List<KitVariant> variants;
    boolean preview;
    boolean closeOnClaim;
    List<KitRequirement> requirements;
    Optional<String> requirementsMaterial;
    Optional<String> requirementsName;
    List<String> requirementsLore;
    List<KitAction> claimActions;
    List<KitAction> denyActions;
    KitSchedule schedule;
    int stockLimit;
    boolean parsePlaceholders;

    KitDefinitionFields(KitDefinition k) {
        this.id = k.id();
        this.items = k.items();
        this.cooldown = k.cooldown();
        this.oneTime = k.oneTime();
        this.permission = k.permission();
        this.cost = k.cost();
        this.displayName = k.displayName();
        this.displayMaterial = k.displayMaterial();
        this.displayLore = k.displayLore();
        this.commands = k.commands();
        this.sound = k.sound();
        this.particles = k.particles();
        this.firstJoin = k.firstJoin();
        this.autoEquip = k.autoEquip();
        this.categoryId = k.categoryId();
        this.claimMoney = k.claimMoney();
        this.claimMoneyCurrency = k.claimMoneyCurrency();
        this.permissionCooldowns = k.permissionCooldowns();
        this.priority = k.priority();
        this.noPermissionMaterial = k.noPermissionMaterial();
        this.noPermissionName = k.noPermissionName();
        this.noPermissionLore = k.noPermissionLore();
        this.cooldownMaterial = k.cooldownMaterial();
        this.cooldownName = k.cooldownName();
        this.cooldownLore = k.cooldownLore();
        this.claimedMaterial = k.claimedMaterial();
        this.claimedName = k.claimedName();
        this.claimedLore = k.claimedLore();
        this.unaffordableMaterial = k.unaffordableMaterial();
        this.unaffordableName = k.unaffordableName();
        this.unaffordableLore = k.unaffordableLore();
        this.customPermission = k.customPermission();
        this.variants = k.variants();
        this.preview = k.preview();
        this.closeOnClaim = k.closeOnClaim();
        this.requirements = k.requirements();
        this.requirementsMaterial = k.requirementsMaterial();
        this.requirementsName = k.requirementsName();
        this.requirementsLore = k.requirementsLore();
        this.claimActions = k.claimActions();
        this.denyActions = k.denyActions();
        this.schedule = k.schedule();
        this.stockLimit = k.stockLimit();
        this.parsePlaceholders = k.parsePlaceholders();
    }

    KitDefinition build() {
        return new KitDefinition(
                id,
                items,
                cooldown,
                oneTime,
                permission,
                cost,
                displayName,
                displayMaterial,
                displayLore,
                commands,
                sound,
                particles,
                firstJoin,
                autoEquip,
                categoryId,
                claimMoney,
                claimMoneyCurrency,
                permissionCooldowns,
                priority,
                noPermissionMaterial,
                noPermissionName,
                noPermissionLore,
                cooldownMaterial,
                cooldownName,
                cooldownLore,
                claimedMaterial,
                claimedName,
                claimedLore,
                unaffordableMaterial,
                unaffordableName,
                unaffordableLore,
                customPermission,
                variants,
                preview,
                closeOnClaim,
                requirements,
                requirementsMaterial,
                requirementsName,
                requirementsLore,
                claimActions,
                denyActions,
                schedule,
                stockLimit,
                parsePlaceholders);
    }
}
