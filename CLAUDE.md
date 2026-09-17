# SAP Commerce Cloud (Hybris) 2211.37

This directory is the platform root (`hybris/`) of a local SAP Commerce Cloud 2211.37
playground installation.

## ⚠️ Version control is partial

**A git repo now exists at this root** (`hybris/`), but it only tracks project code and the
agentic setup — `bin/custom`, `.claude`, `CLAUDE.md`, `requirements/` (see `.gitignore`). Use
`git diff`/`git log`/`git status` freely for anything inside that scope to see what actually
changed. Committing still follows normal git-safety practice — only when the user asks, never
unprompted.

**Everything else is still untracked and has no way to undo**: `bin/platform`, `bin/modules`,
`config/`, `data/`, `log/`, and any other path outside the tracked scope above. Treat those exactly
like before — read before overwriting, copy aside before a risky change, never bulk-rewrite or
bulk-delete without saying so first. Don't assume `git status`/`git diff` shows changes there; it
won't, because those paths are gitignored, not because nothing changed.

`origin` is `github.com/renatoh/agentic-hybris-development` (public). `gh` is installed — `gh auth
login` is a one-time, user-run step; once done, `gh pr create`/`gh pr view --comments`/etc. work
without a token ever passing through an agent or this conversation.

### Feature branches and PRs

**Every ticket, and every sub-ticket spun off during one** (a review finding, a small addition like
"also add a delete button"), gets its own feature branch — never commit feature work straight to
`main`.

- **Branch name**: `<agent-name>/<ticket-id>[-<short-slug>]` — e.g. `hybris-backend-developer/NET-8940`,
  or for sub-ticket work with no formal ticket id, `hybris-backend-developer/NET-8940-delete-list`.
  The agent name in the branch name is what makes a `git branch -a` / GitHub branch list tell you at
  a glance which agent did the work, same purpose as the `Co-Authored-By: Claude ...` trailer on the
  commit itself (keep including that too).
- **`hybris-backend-developer` creates the branch, implements, and commits on it** — using the
  private-identity env-var override on the commit (`GIT_AUTHOR_NAME`/`GIT_AUTHOR_EMAIL`/
  `GIT_COMMITTER_NAME`/`GIT_COMMITTER_EMAIL`; never `git config`), same as the existing standing
  practice.
- **Only one agent switches branches at a time.** This is one shared working directory, not isolated
  worktrees per agent — a branch switch changes what every other pane sees on disk. Follow the same
  rule as the Herdr "never run two agents on the same files" rule above: `hybris-backend-reviewer`
  and `hybris-junit-writer` work against whatever's currently checked out, so don't switch branches
  while either is mid-task.
- **`hybris-backend-developer` opens the PR itself once `hybris-backend-reviewer` is satisfied** —
  not before, and not automatically the moment a review comes back clean; the developer pushes the
  branch and runs `gh pr create --base main --head <branch>` after addressing every finding, per the
  existing "before reporting work as done" checklist. `hybris-backend-reviewer` stays read-only —
  it never pushes or opens the PR itself, only reports findings.

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

**Localization: English only.** Add/edit message keys in `base_en.properties` (or the extension's
own `*-locales_en.properties`) only — do not touch the other locale bundles (`base_de.properties`,
`base_fr.properties`, etc.) that ship with the accelerator template. They exist because the template
includes them, not because this project supports those languages.

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
2. **One pane per agent definition, reused across sub-tasks within a ticket — but a new ticket
   starts a fresh pane.** Run `herdr agent list` first: if a **live** agent of that definition
   exists *and is working the same ticket*, route the new work into it (`herdr agent prompt <name>
   "..."`), briefed with just what's new. A **new ticket does not reuse the previous ticket's
   context** — even if the pane is still live, rename its agent aside (e.g. append `-<ticket-id>`)
   to free the canonical name, then start a fresh one for the new ticket. Don't let one pane's
   context accumulate across unrelated tickets — it's wasted tokens for context the new ticket
   doesn't need, and risks the same thing that happened once already: a pane's context grew large
   enough across a long ticket that it hit its own session limit mid-task. An exited agent's context
   is gone either way, so a fresh start costs nothing extra.
3. **Open a pane**: `herdr pane split --current --direction right|down --cwd "$PWD" --no-focus`,
   then `herdr agent start <definition-name> --kind claude --pane <id>`. Wide pane → split right,
   narrow/tall → down. Keep the user's focus where it is.
4. **Never run two agents on the same files** — git doesn't prevent simultaneous edits, only shows
   the damage after the fact; concurrent writers still clobber each other silently, worse still
   outside the tracked `bin/custom`/`.claude` scope where there's no diff to recover from at all.
   If a *different* agent definition already owns that area, wait.
5. **Clean up only what you created** — stop servers/watchers before finishing; don't close panes,
   tabs or workspaces you didn't open.

A pane agent is a **separate Claude session**, not a subagent — no inherited context, no automatic
result. Brief it completely (task, binding spec, environment constraints) and read its report from
the pane.

## Before reporting work as done

1. Did an `items.xml` / `beans.xml` change need `ant clean all` plus a system update
   (`hac` → Platform → Update) or a type-system migration? Say so explicitly.
2. Are new or changed types covered by ImpEx (essential vs. project data) under `resources/impex/`?
3. Is anything localized (`localized:java.lang.String`) handled for English — and English only (see
   Layout above)? Don't add or edit message keys in other locale bundles.
4. Are transactions, `SessionService` / `SearchRestrictions` and catalog-version awareness
   handled where relevant?
5. Are new services registered in Spring and covered by a test in `testsrc`?
6. State precisely what still needs a build, a system update, or a server restart to take effect.
7. **Beyond a one-line fix, has `hybris-backend-reviewer` actually reviewed it?** Not optional. A
   green build and passing tests prove it runs, not that it's right (real examples of the gap in
   the `sap-commerce-cloud` skill). Send it the changed paths per the Herdr rules above, and
   address or consciously reject every finding before calling the work done. Once satisfied, open
   the PR (see "Feature branches and PRs" above) — that's the actual last step, not a separate task.

A green build is not proof that a page works. When a change affects the storefront, open the
actual page and confirm it renders.

## Specs

Requirement documents in `requirements/` are agreed with the user and are binding. Implement them
as written; if a spec turns out to be wrong (a method that does not exist, a bean that is not
there), stop and report rather than redesigning it silently.
