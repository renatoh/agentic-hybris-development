# SAP Commerce Cloud (Hybris) 2211.37

This directory is the platform root (`hybris/`) of a local SAP Commerce Cloud 2211.37
playground installation.

## ⚠️ No version control

**This directory is not a git repository.** There is no `.git`, no branches, no `git diff`,
no way to undo. Every edit is immediate and unrecoverable.

- Read a file before overwriting it.
- Before a risky or wide-reaching change, copy the affected files aside first.
- Never bulk-rewrite or bulk-delete without saying what you are about to do.

## Layout

| Path | What it is |
|---|---|
| `bin/platform` | Platform sources and the ant build. **Never modify.** |
| `bin/modules` | SAP-provided extensions (incl. the storefronts and JSPs). **Never modify.** |
| `bin/custom` | Project code — `customservices`, and our own cloned accelerator stack under `bin/custom/custom/` (`customcore`, `customfacades`, `customstorefront`, `custombackoffice`, `customfulfilmentprocess`, `custominitialdata`, `customtest`) |
| `config` | `local.properties`, `localextensions.xml` |
| `requirements/` | Agreed requirement specs (e.g. `NET-8938-order-history-via-rest.md`) |
| `<ext>/gensrc` | Generated model classes — build output |

New project code goes in `bin/custom/`. If a fix appears to require editing `bin/platform`
or `bin/modules`, do not do it — propose an override in a custom extension and report the
choice instead.

**The active storefront is `customstorefront`** (`bin/custom/custom/customstorefront`), our own
clone of the accelerator (`ant extgen -Dinput.template=...`). `yacceleratorcore`,
`yacceleratorfacades` and `yacceleratorstorefront` are commented out in `config/localextensions.xml`
in favour of the `custom*` equivalents — treat the `custom*` stack as project code (safe to edit),
not as `bin/modules`. `yb2bacceleratorstorefront` also exists under `bin/custom` but is commented
out — do not assume it is in play. Current scope is B2C only.

**Design for multiple stores, even though only one is live today.** Never hardcode a catalog/store
into a global property — scope store-specific values as data instead. Full detail in the
`sap-commerce-cloud` skill; load it before designing anything touching products, catalogs, or a
cronjob.

## Build and run

Exact commands (JAVA_HOME, ant, hybrisserver.sh) are in the `sap-commerce-cloud` skill — load it
before building. Standing rules regardless:

- **`ant initialize` is destructive** (wipes and rebuilds the entire local DB) — **never run it as
  an agent**, always ask the user first.
- **`ant updatesystem` is pre-approved** for any agent to run freely, specifically to verify that
  custom ImpEx is actually wired for automatic import (see the `sap-commerce-cloud` skill). It still
  rewrites the schema, so don't run it incidentally — only when actually verifying ImpEx/type-system
  wiring.
- **Any new or changed custom ImpEx must be registered so it also runs under a future
  `ant initialize`**, not just imported once by hand — see the skill for the `SystemSetup` pattern.
  Verify this with `ant updatesystem` (pre-approved); never run `ant initialize` to test it.
- Check for an already-running server before `ant all` (it fails against a live one) — and stop
  the server yourself when done; never leave a server or log watcher running at the end of a task.
- Builds and server startup exceed the default command timeout — run them in the background and
  poll. A timeout is not a failure.

## Never edit generated models

`*Model.java` under `<ext>/gensrc` is build output. To change a type, edit the extension's
`*-items.xml` and rebuild. Editing a generated model is silently discarded on the next build.

## Layers, Spring conventions, and configuration

ServiceLayer architecture (model/DAO/service/facade/converter split), items.xml, and Spring bean
conventions are covered in the `sap-commerce-cloud` skill — load it before writing or reviewing
any hybris backend code. Two standing rules regardless: property defaults go in
`<ext>/project.properties`, environment values in `config/local.properties`, read via
`configurationService.getConfiguration()` — and **do not edit `config/local.properties`
yourself**; report what the operator needs to set there.

## Tests

There is **no junit tenant configured** (`installed.tenants=` is empty in `local.properties`), so
`ant unittests` does not work in this installation. Run tests directly with `JUnitCore` against
the platform classpath instead. Do not try to configure a tenant without asking.

Unit tests live in `<ext>/testsrc`, annotated `@UnitTest` or `@IntegrationTest`.

## Agents

Agent definitions live in `.claude/agents/`:

- `hybris-backend-developer.md` — backend work inside hybris extensions: items.xml modelling, the
  Spring/service/DAO/facade/converter layers, ImpEx, FlexibleSearch, interceptors, cronjobs, OCC
  controllers, ant/ServiceLayer troubleshooting. Hands off interface/DTO shapes to
  `hybris-junit-writer` early — as soon as a layer's signatures are settled, not after the whole
  feature is implemented — so test-writing runs in parallel (see that file's own section on it).
- `hybris-junit-writer.md` — writes JUnit tests under `testsrc/` from a class-shape brief, in
  parallel with backend implementation. Never edits `src/**` or anything outside `testsrc/**`.
- `hybris-backend-reviewer.md` — independent review of that work (read-only, reports findings).

**If no existing agent definition fits the work, say so and ask before creating a new one.**
Never write a new file into `.claude/agents/` unprompted.

### Run delegated work in a visible Herdr panel

When the `herdr` skill is available (`HERDR_ENV=1`), delegated or long-running work must happen in
a **visible Herdr pane**, not invisibly in the background. The point is that the work can be watched
as it happens.

1. **Only start a pre-defined agent** — one of `hybris-backend-developer`, `hybris-junit-writer`,
   `hybris-backend-reviewer`. Never invent an ad-hoc persona/name (`net8939-backend`,
   `hybris-detail`). If none fits, ask the user rather than improvising a generic brief.
2. **One pane per agent definition, reused across tasks** — keyed by *which agent*, not which
   ticket. Run `herdr agent list` first: if a **live** agent of that definition exists, route the
   new work into it (`herdr agent prompt <name> "..."`), briefed with just what's new. Otherwise
   open one new pane and name the agent after the definition, never the task — an exited agent's
   context is gone, so resuming is a cold start no different from starting fresh.
3. **Open a pane**: `herdr pane split --current --direction right|down --cwd "$PWD" --no-focus`,
   then `herdr agent start <definition-name> --kind claude --pane <id>`. Wide pane → split right,
   narrow/tall → down. Keep the user's focus where it is.
4. **Never run two agents on the same files** — no version control here, concurrent writers
   clobber each other silently. If a *different* agent definition already owns that area, wait.
5. **Clean up only what you created** — stop servers/watchers before finishing; don't close panes,
   tabs or workspaces you didn't open.

A pane agent is a **separate Claude session**, not a subagent — no inherited context, no automatic
result. Brief it completely (task, binding spec, environment constraints) and read its report from
the pane.

## Before reporting work as done

1. Did an `items.xml` / `beans.xml` change need `ant clean all` plus a system update
   (`hac` → Platform → Update) or a type-system migration? Say so explicitly.
2. Are new or changed types covered by ImpEx (essential vs. project data) under `resources/impex/`?
3. Is anything localized (`localized:java.lang.String`) handled for all configured languages?
4. Are transactions, `SessionService` / `SearchRestrictions` and catalog-version awareness
   handled where relevant?
5. Are new services registered in Spring and covered by a test in `testsrc`?
6. State precisely what still needs a build, a system update, or a server restart to take effect.
7. **Beyond a one-line fix, has `hybris-backend-reviewer` actually reviewed it?** Not optional. A
   green build and passing tests prove it runs, not that it's right (real examples of the gap in
   the `sap-commerce-cloud` skill). Send it the changed paths per the Herdr rules above, and
   address or consciously reject every finding before calling the work done.

A green build is not proof that a page works. When a change affects the storefront, open the
actual page and confirm it renders.

## Specs

Requirement documents in `requirements/` are agreed with the user and are binding. Implement them
as written; if a spec turns out to be wrong (a method that does not exist, a bean that is not
there), stop and report rather than redesigning it silently.
