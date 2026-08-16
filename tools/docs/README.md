# Documentation tooling

The module pages on docs.uxplima.com take their command, permission, setting and placeholder tables from the
plugin itself. Two steps:

```bash
./gradlew :bukkit-adapter:docsExport
python3 tools/docs/generate.py bukkit-adapter/build/docs/docs-data.json ../docs-content/content
```

`generate.py` only rewrites the text between `{/* generated:x */}` and `{/* /generated */}` in
`content/minecraft/plugins/uxmessentials/modules/<module>.md`. Prose outside those markers is never touched, and a
second run over an unchanged model produces no diff. The markers are MDX comments rather than HTML ones
because the docs platform compiles every page as MDX, where `<!-- -->` is a parse error.

A page needs a marker pair for exactly the sections its module has data for. A missing pair, a spare pair or a
duplicate one fails the run rather than being quietly skipped. `--require-all` additionally fails when a
registered module has no page, which is what the finished migration is checked with.

The export reads four sources, all of them already guarded by the build: the module registry for which modules
exist, `PermissionCatalog` and `PlaceholderCatalog` for the nodes and keys each one owns, `command-surface.txt`
for aliases, and the shipped `modules/<id>/config.conf` for the settings and their explanatory comments. The
modules that register commands from their own wiring rather than as a `CommandSpec` are found by the context
package their command classes sit in.

Run the tests with `cd tools/docs && python3 -m unittest test_generate.py`.
