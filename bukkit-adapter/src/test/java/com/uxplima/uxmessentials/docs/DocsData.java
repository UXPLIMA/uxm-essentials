package com.uxplima.uxmessentials.docs;

import java.util.List;

/** The shape the documentation exporter hands to the page generator. */
final class DocsData {

    record Command(String literal, List<String> aliases, String permission, String description) {}

    record Permission(String node, String fallback, String shape, String description) {}

    record Setting(String key, String value, String description) {}

    record Placeholder(String key, String scope, String description) {}

    record Module(
            String id,
            String configPath,
            boolean enabledByDefault,
            List<Command> commands,
            List<Permission> permissions,
            List<Setting> settings,
            List<Placeholder> placeholders) {}

    private DocsData() {}
}
