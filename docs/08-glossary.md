# 8. Glossary

Where the words are defined, and the few that belong to the whole plugin rather than to one
context.

## 8.1 A context's glossary is its `package-info.java`

The canon used to ask for a `GLOSSARY.md` in every bounded context and claimed a guard held
the set complete. Neither was true: not one was ever written, the guard did not exist, and
the documents accumulated forty odd links into files that were not there.

What the codebase grew instead is the better version of the same idea. Every layer package of
every context carries a `package-info.java` that says what the package owns, which invariants
it enforces and what may not appear in it. It sits next to the code it describes, the
compiler sees it, review sees it, and it cannot drift away from its package without somebody
noticing.

`UbiquitousLanguageDriftTest` enforces it across `:core`, where the domain and application
layers live, and it asks twice: every package has one, and every context describes its
application layer specifically. A package with no `package-info.java` is also invisible to
NullAway, because that file is where `@NullMarked` lives, so a missing glossary is a missing
null-safety fence as well. That is why it is enforced rather than encouraged.

## 8.2 The words that belong to no single context

| Word | What it means here |
|---|---|
| Bounded context | One feature with its own package, config file, commands and switch. A module. |
| Kernel | `shared`: what the contexts stand on. Not a context and owns no feature. |
| Port | An interface a context declares for something outside itself. |
| Adapter | An implementation of a port, or a translation of an event into a use case call. |
| Composition root | `bootstrap`, the one place that builds the graph and names `JavaPlugin`. |
| Snapshot | An immutable read of a configuration file, swapped in whole so no reader sees half a reload. |
| Spec | A declarative description read from a file: a menu spec, a command spec. |
| Sink | Where a finished message goes: a player, the console, a channel. |
| Lane | Which thread a piece of work belongs on: global, region, entity or async. |
| Family | A permission node completed with a number or a name, as in `uxmessentials.home.limit.<n>`. |
| Audit line | One structured `event=` line on the audit channel, for an action somebody may have to answer for. |
| Frame | One message on the cross-server bus, stamped with the id of the backend that sent it. |
| Drift guard | A test that fails the build the day a written rule stops being true. |

A word in this table means this in every context. A word that means something in one context
belongs in that context's `package-info.java`, not here.
