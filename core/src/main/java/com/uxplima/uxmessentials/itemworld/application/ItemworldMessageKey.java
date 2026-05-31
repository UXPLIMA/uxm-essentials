package com.uxplima.uxmessentials.itemworld.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The itemworld context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code GIVE_GIVEN} ↔ {@code itemworld.give.given}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in the
 * context — every message resolves through one of these.
 *
 * <p>The keys cover the full ~40-verb surface grouped by sub-feature group (docs/10-feature-modules.md
 * §15.10): item utils, virtual workstations, cleanup, powertool, mob/entity, time/weather (and its aliases),
 * and admin-fun, plus the shared validation failures the adapter boundary surfaces before a domain call
 * (unknown item, amount over cap, bad enchant level, bad time/weather, unknown mob). Per the i18n contract, a
 * disabled module still ships its keys so the catalog stays whole and the locale-parity guard sees the full
 * {@code en} key set.
 */
public enum ItemworldMessageKey implements MessageKey {

    // Item utils — /give, /i
    GIVE_GIVEN("itemworld.give.given"),
    GIVE_RECEIVED("itemworld.give.received"),
    // /item
    ITEM_GIVEN("itemworld.item.given"),
    // /itemname
    ITEMNAME_SET("itemworld.itemname.set"),
    ITEMNAME_CLEARED("itemworld.itemname.cleared"),
    // /itemlore
    ITEMLORE_SET("itemworld.itemlore.set"),
    ITEMLORE_ADDED("itemworld.itemlore.added"),
    ITEMLORE_CLEARED("itemworld.itemlore.cleared"),
    // /itemflag
    ITEMFLAG_TOGGLED("itemworld.itemflag.toggled"),
    ITEMFLAG_UNKNOWN("itemworld.itemflag.unknown"),
    // /skull
    SKULL_GIVEN("itemworld.skull.given"),
    // /more
    MORE_FILLED("itemworld.more.filled"),
    MORE_ALREADY_FULL("itemworld.more.already-full"),
    // /repair, /repairall
    REPAIR_DONE("itemworld.repair.done"),
    REPAIR_NOTHING("itemworld.repair.nothing"),
    REPAIRALL_DONE("itemworld.repairall.done"),
    // /enchant
    ENCHANT_APPLIED("itemworld.enchant.applied"),
    ENCHANT_UNKNOWN("itemworld.enchant.unknown"),
    ENCHANT_LEVEL_CLAMPED("itemworld.enchant.level-clamped"),
    // /hat
    HAT_WORN("itemworld.hat.worn"),
    HAT_NO_ITEM("itemworld.hat.no-item"),
    // /itemdb
    ITEMDB_REPORT("itemworld.itemdb.report"),
    // /unbreakable
    UNBREAKABLE_SET("itemworld.unbreakable.set"),
    // /disenchant
    DISENCHANT_ONE("itemworld.disenchant.one"),
    DISENCHANT_ALL("itemworld.disenchant.all"),
    DISENCHANT_NONE("itemworld.disenchant.none"),
    DISENCHANT_NOT_PRESENT("itemworld.disenchant.not-present"),
    // /itemmodel
    ITEMMODEL_SET("itemworld.itemmodel.set"),
    ITEMMODEL_CLEARED("itemworld.itemmodel.cleared"),
    // /editsign
    EDITSIGN_OPENED("itemworld.editsign.opened"),
    EDITSIGN_NOT_A_SIGN("itemworld.editsign.not-a-sign"),
    EDITSIGN_NO_ACCESS("itemworld.editsign.no-access"),

    // Virtual workstations — one opened line, parameterised by station name
    WORKSTATION_OPENED("itemworld.workstation.opened"),

    // Cleanup — /disposal
    DISPOSAL_OPENED("itemworld.disposal.opened"),
    // /condense
    CONDENSE_DONE("itemworld.condense.done"),
    CONDENSE_NOTHING("itemworld.condense.nothing"),

    // Powertool — /powertool
    POWERTOOL_BOUND("itemworld.powertool.bound"),
    POWERTOOL_CLEARED("itemworld.powertool.cleared"),
    POWERTOOL_NO_ITEM("itemworld.powertool.no-item"),
    // /powertooltoggle
    POWERTOOL_ENABLED("itemworld.powertool.enabled"),
    POWERTOOL_DISABLED("itemworld.powertool.disabled"),

    // Mob & entity — /spawnmob
    SPAWNMOB_SPAWNED("itemworld.spawnmob.spawned"),
    SPAWNMOB_UNKNOWN("itemworld.spawnmob.unknown"),
    // /spawner
    SPAWNER_SET("itemworld.spawner.set"),
    SPAWNER_NOT_A_SPAWNER("itemworld.spawner.not-a-spawner"),
    // /kill
    KILL_DONE("itemworld.kill.done"),
    // /butcher
    BUTCHER_DONE("itemworld.butcher.done"),
    // /killall
    KILLALL_DONE("itemworld.killall.done"),
    // /remove
    REMOVE_DONE("itemworld.remove.done"),
    // /unlimited
    UNLIMITED_ENABLED("itemworld.unlimited.enabled"),
    UNLIMITED_DISABLED("itemworld.unlimited.disabled"),

    // Time & weather — /time, /day, /night
    TIME_SET("itemworld.time.set"),
    // /weather, /sun, /rain, /thunder
    WEATHER_SET("itemworld.weather.set"),

    // Admin-fun — /lightning
    LIGHTNING_STRUCK("itemworld.lightning.struck"),
    // /fireball
    FIREBALL_LAUNCHED("itemworld.fireball.launched"),
    // /kittycannon
    KITTYCANNON_FIRED("itemworld.kittycannon.fired"),

    // Shared validation failures surfaced at the adapter boundary before a domain call
    UNKNOWN_ITEM("itemworld.unknown-item"),
    AMOUNT_OUT_OF_RANGE("itemworld.amount-out-of-range"),
    BAD_TIME("itemworld.bad-time"),
    BAD_WEATHER("itemworld.bad-weather"),
    UNKNOWN_TARGET("itemworld.unknown-target"),
    NO_ITEM_IN_HAND("itemworld.no-item-in-hand"),
    COMMAND_DISABLED("itemworld.command-disabled");

    private final String key;

    ItemworldMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
