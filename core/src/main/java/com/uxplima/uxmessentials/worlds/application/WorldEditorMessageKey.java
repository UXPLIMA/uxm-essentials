package com.uxplima.uxmessentials.worlds.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The worlds GUI editor's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog
 * key under {@code world.editor.*} in {@code messages_<lang>.conf}; the constant is the compile-time
 * handle, the catalog holds the text. These render inventory titles and item name/lore Components, so
 * unlike chat keys they carry no {@code <prefix>}. No inline player-facing literals exist in the views.
 */
public enum WorldEditorMessageKey implements MessageKey {
    LIST_TITLE("world.editor.list-title"),
    CREATE_TITLE("world.editor.create-title"),
    MAIN_TITLE("world.editor.main-title"),
    RULES_TITLE("world.editor.rules-title"),
    GENERATION_TITLE("world.editor.generation-title"),
    ACCESS_TITLE("world.editor.access-title"),

    NAV_CREATE("world.editor.nav.create"),
    NAV_RULES("world.editor.nav.rules"),
    NAV_GENERATION("world.editor.nav.generation"),
    NAV_ACCESS("world.editor.nav.access"),
    NAV_BACK("world.editor.nav.back"),
    NAV_LOAD("world.editor.nav.load"),
    NAV_UNLOAD("world.editor.nav.unload"),
    NAV_PREV("world.editor.nav.prev"),
    NAV_NEXT("world.editor.nav.next"),

    LIST_ENTRY_NAME("world.editor.list.entry-name"),
    LIST_ENTRY_LORE("world.editor.list.entry-lore"),

    CREATE_BUTTON_NAME("world.editor.create.button-name"),
    CREATE_BUTTON_LORE("world.editor.create.button-lore"),
    CREATE_NAME("world.editor.create.name"),
    CREATE_NAME_LORE("world.editor.create.name-lore"),
    CREATE_NAME_PROMPT("world.editor.create.name-prompt"),
    CREATE_NAME_REQUIRED("world.editor.create.name-required"),
    CREATE_ENVIRONMENT("world.editor.create.environment"),
    CREATE_TYPE("world.editor.create.type"),
    CREATE_GENERATOR("world.editor.create.generator"),
    CREATE_SEED("world.editor.create.seed"),
    CREATE_SEED_LORE("world.editor.create.seed-lore"),
    CREATE_SEED_PROMPT("world.editor.create.seed-prompt"),
    CREATE_SEED_INVALID("world.editor.create.seed-invalid"),
    CREATE_CYCLE_HINT("world.editor.create.cycle-hint"),
    CREATE_CONFIRM("world.editor.create.confirm"),
    CREATE_CONFIRM_LORE("world.editor.create.confirm-lore"),

    MAIN_SUMMARY_NAME("world.editor.main.summary-name"),
    MAIN_SUMMARY_LORE("world.editor.main.summary-lore"),

    GEN_ENVIRONMENT("world.editor.gen.environment"),
    GEN_TYPE("world.editor.gen.type"),
    GEN_GENERATOR("world.editor.gen.generator"),
    GEN_SEED("world.editor.gen.seed"),

    PROPERTY_LORE("world.editor.property.lore"),

    PROP_PVP("world.editor.prop.pvp"),
    PROP_DIFFICULTY("world.editor.prop.difficulty"),
    PROP_FORCE_GAMEMODE("world.editor.prop.force-gamemode"),
    PROP_SPAWN_ANIMALS("world.editor.prop.spawn-animals"),
    PROP_SPAWN_MONSTERS("world.editor.prop.spawn-monsters"),
    PROP_TIME("world.editor.prop.time"),
    PROP_WEATHER("world.editor.prop.weather"),
    PROP_ACCESS_RESTRICTED("world.editor.prop.access-restricted"),
    PROP_PLAYER_LIMIT("world.editor.prop.player-limit"),
    PROP_ENTRY_FEE("world.editor.prop.entry-fee"),
    PROP_PORTAL_NETHER_LINK("world.editor.prop.portal-nether-link"),
    PROP_PORTAL_END_LINK("world.editor.prop.portal-end-link");

    private final String key;

    WorldEditorMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
