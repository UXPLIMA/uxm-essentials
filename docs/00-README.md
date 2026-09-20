# The uxmEssentials documentation

## What is here

| Document | Holds |
|---|---|
| `09-deployment.md` | The five jars, the three paths, the audit channel and what an upgrade does |
| `permissions.md` | The node space, the four shapes, the numbered-node convention and the gates |
| `10-feature-modules.md` | What a module is, the eight method contract, and how to add one |
| `11-economy-integration.md` | The three layers money crosses, and the rules that cost something |
| `02-concurrency.md` | The four lanes, the scheduler port, and the enumerate then hop rule |
| `notes/` | The lessons and the open defects, dated, one file each |

## What is cited and not here yet

The javadoc of this repository points at twelve documents, 183 times. Seven of them are not
written. They are listed here so that a reader who follows a reference knows why it goes
nowhere, and so the order of writing them is the order they are wanted in:

| Document | References |
|---|---|
| `05-testing.md` | 4 |
| `01-architecture.md` | 4 |
| `14-ui-style.md` | 3 |
| `08-glossary.md` | 2 |
| `04-build.md` | 2 |
| `03-paper-api.md` | 2 |
| `06-code-quality.md` | 1 |

Each is written from the code that cites it, most cited first, and each one says at the top
that it was.

## Until one exists, read the guard

Every rule those documents would state is already enforced by a test in
`bukkit-adapter/src/test/java/com/uxplima/uxmessentials/architecture/`, and each guard names
the rule it keeps and the defect that paid for it. A guard cannot go stale: it fails the build
the day the rule stops being true. `CLAUDE.md` section 3 lists them against the rules.
