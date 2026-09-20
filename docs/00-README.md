# The uxmEssentials documentation

## What is here

| Document | Holds |
|---|---|
| `12-migration.md` | The importer, what each source maps, the runbook and the config ladder |
| `13-i18n.md` | The key, the locale chain, the twelve catalogues and the parity gate |
| `09-deployment.md` | The five jars, the three paths, the audit channel and what an upgrade does |
| `permissions.md` | The node space, the four shapes, the numbered-node convention and the gates |
| `05-testing.md` | Which layer gets which test, the contract suite, and the two kinds of guard |
| `01-architecture.md` | The four layers, the ten build modules, and the persistence invariants |
| `03-paper-api.md` | Which platform API is used, the manifest, the PDC rule and the text path |
| `04-build.md` | The catalogue, what is shaded, and the gates `check` runs |
| `06-code-quality.md` | Warnings, swallowed failures, sizes and comments |
| `08-glossary.md` | Where a context's words are defined, and the words that belong to all of them |
| `14-ui-style.md` | One look: the palette, MiniMessage, the lore layout and the widths |
| `10-feature-modules.md` | What a module is, the eight method contract, and how to add one |
| `11-economy-integration.md` | The three layers money crosses, and the rules that cost something |
| `02-concurrency.md` | The four lanes, the scheduler port, and the enumerate then hop rule |
| `notes/` | The lessons and the open defects, dated, one file each |

## Every document this repository cites now exists

The javadoc points at fourteen documents, 401 times, and all fourteen are written. Each was
written from the call sites that cite it, and each says so at the top, so a section number in
a javadoc comment resolves to a section that answers it.

The count was wrong for a while in a way worth remembering: it read only the references
written with a `.md` on the end, which missed `12-migration` at 135 references, the most
cited document here, and `13-i18n` at 19. A reference is `docs/12-migration §5.1` as often as
it is `docs/12-migration.md`.

## The guard is the other half

Every rule these documents state is also enforced by a test in
`bukkit-adapter/src/test/java/com/uxplima/uxmessentials/architecture/`, and each guard names
the rule it keeps and the defect that paid for it. A guard cannot go stale: it fails the build
the day the rule stops being true. `CLAUDE.md` section 3 lists them against the rules.
