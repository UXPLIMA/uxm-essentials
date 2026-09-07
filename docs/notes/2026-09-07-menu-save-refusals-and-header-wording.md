# What a menu save now refuses, and what the header still does not say

2026-09-07. Written while moving this repository off its own `MenuSpecWriter` and onto uxmLib's
`com.uxplima.uxmlib.menu.spec.MenuSpecWriter`. Two decisions belong to the owner, so they are
written down rather than settled here.

## 1. The properties editor can build a menu the writer will not spell

uxmLib's writer refuses three spec shapes rather than dropping the part it cannot write. Two of
them are reachable from this plugin's editor, and by the same route.

`MenuPropertiesView` offers **rows**, **inventory-type** and **bottom-inventory** as three
independent fields. A bottom-inventory menu is six rows and chest-only by definition: the raw-slot
geometry that paints into the viewer's own inventory only lines up for a full double chest, and
`MenuSpecLoader` pins both facts on load, dropping a declared `inventory-type` with a warning. So
an operator can leave a bottom-inventory menu at three rows, or give it a hopper shape, and the
model is valid Java that no menu file can hold.

- **Before this move**, the local writer wrote such a spec out happily. The next load pinned rows
  back to six and dropped the type. The operator got a menu that was not the one they saved, and
  nothing told them.
- **Now**, the save fails. `MenuSpecPersistence.save` catches the `MenuSpecException` and returns
  `Status.IO_ERROR`, with the writer's own sentence in the server log. Nothing is written and the
  file on disk is untouched. `MenuSpecPersistenceTest` pins both cases.

The refusal is right. The reporting is not finished, and that is the open question:

1. **Give it its own status.** `Status.UNWRITABLE`, its own `EditOutcome`, and a message key that
   names the field. That is a new key in every shipped locale, so it is a locale-parity commit of
   its own.
2. **Stop the editor building it.** Grey the rows stepper and the inventory-type selector out while
   bottom-inventory is on, which is what the model already means.
3. **Leave it.** The operator sees the generic save-failed line and an admin reads the log.

Option 3 is what ships today, because it is the smallest thing that is not a stack trace. Option 2
is the one that matches the rest of the editor, since every other field the editor offers can be
saved.

The third refusal, a `Ref` carrying an argument other than `value`, and the fourth, a non-empty
`ClickSpec.conditions()`, are **not reachable here**. Every ref this plugin builds comes from
`Ref.parse` or from the loader, and both put the whole tail under `value`; every `ClickSpec` this
plugin constructs passes an empty condition map, and `withGestureActions` / `withGestureRequirement`
carry that emptiness forward. Nothing to do, recorded so the next reader does not re-derive it.

## 2. The header tells an operator two of the five things a save canonicalises

`menus/header.txt` says a save keeps the header, does not keep a comment the operator wrote, and
sorts the keys. All three are still exactly true.

It does not mention the shorthands, and the library's writer canonicalises four of them:

| the operator wrote | a save returns |
|---|---|
| `fill-item { ... }` | an ordinary item under `items.__fill__`, with the slots it actually claimed |
| `update-interval = 20` | the `refresh { enabled, interval-ticks }` block it means |
| `pattern` / `vars` | the item already expanded |
| `layout` grid characters | each item carrying its own concrete `slots` |

None of these loses the menu: the round trip is model faithful and `MenuSpecRoundTripTest` proves
it key by key. They change what the operator's file looks like the next time they open it.

The `fill-item` row is the one with a behaviour tail. The loader computes a fill's slots from
whichever slots no other item holds, and the writer freezes that answer. So after a save the fill
no longer recomputes: a real item added later still wins, because the fill keeps the lowest
priority, but a slot freed by hand-deleting an item afterwards is no longer filled. The local
writer re-emitted the `fill-item` block and kept the recompute, which is the one point on which it
was not the poorer of the two.

**The decision for the owner** is where this is said: three more sentences in `header.txt` (which
means the same three in `menus/example.conf`, since `MenuSpecHeaderTest` holds the two together),
or a paragraph in the docs page for the menu editor, or nowhere because the shapes are rare.
Nothing was edited here, because the header is operator-facing wording and the wording is the
owner's.
