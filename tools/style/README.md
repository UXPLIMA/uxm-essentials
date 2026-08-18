# Style tools

Scripts that keep the interface in the shape `docs/14-ui-style.md` fixes. They operate on shipped
resources, are idempotent, and print what they changed.

| Script | What it does |
|---|---|
| `smallcaps.py` | Rewrites a message catalog into small capitals, drops emoji and trailing status glyphs, and replaces em dashes. Tag names, placeholders, papi tokens and command literals are left alone. |
| `restyle_config.py` | The same rewrite for the player-visible strings in a shipped operator config, recognised by the style token they carry. |
| `nav.py` | Gives every pagination and back key the single-line arrow shape. |
| `lore.py` | Builds a menu item's lore in the canonical order (title, breadcrumb, description, information, actions). |
| `apply_lore.py` | Writes generated lore blocks back into the catalog by key. |
| `modules/<module>.py` | One file per module describing its items through `lore.py`, then applying them. |

Run from the repository root:

```bash
python3 tools/style/smallcaps.py bukkit-adapter/src/messages/resources/messages/messages_en.conf
python3 tools/style/restyle_config.py bukkit-adapter/src/main/resources/modules/scoreboard/config.conf
python3 tools/style/nav.py
python3 tools/style/modules/homes.py
```

A rewrite is followed by `./gradlew spotlessApply` and `./gradlew check`, as any other change is.
