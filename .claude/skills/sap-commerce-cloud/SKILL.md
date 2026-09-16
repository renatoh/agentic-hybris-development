---
name: sap-commerce-cloud
description: SAP Commerce Cloud (Hybris) 2211 platform knowledge — items.xml syntax pitfalls, CronJob/Job wiring, ServiceLayer batching patterns, build/server lifecycle quirks, and the standing multi-basestore/multi-shop design requirement for this installation. Load before writing or reviewing any code under bin/custom/**, before touching items.xml, Spring cronjob wiring, ImpEx, or the ant/hybrisserver.sh lifecycle — by any agent, not only the three hybris-specific personas.
---

# SAP Commerce Cloud (Hybris) 2211 — platform knowledge

Kept separate from `CLAUDE.md` and the agent personas so any agent — not only the three
hybris-specific ones — can load it. Everything here was verified against real platform sources or
reproduced directly in this installation; installation-specific facts are marked as such.

## Multi-basestore / multi-shop is a standing design requirement

**This installation is expected to grow to more than one `BaseStore`/shop, each with its own
product catalog.** Every new feature must be designed for that from the start, not bolted on
later:

- **Never** put a catalog id, catalog version, store uid, or any other store-scoped value in a
  global `project.properties`/`local.properties` property. A property file has one value; a
  second store breaks it silently or requires a code change to fix.
- **Never** resolve "the current store" implicitly from session state in code that runs outside a
  storefront request (a cronjob, a batch import) — there is no session/current-store to resolve
  from in that context, and reaching for one anyway (e.g. `CatalogVersionService.
  getSessionCatalogVersion()`) will misbehave the moment more than one instance of the job is
  running for more than one store.
- **Do** scope store-specific configuration as data, not properties: an attribute on a dedicated
  `CronJob` subtype (see below), a `BaseStore` reference, or an explicit parameter threaded through
  the call from a per-store entry point. The standard shape for a per-store scheduled job: one
  `Job` Spring bean, one `CronJob` *instance* per store, each carrying its own
  tenant/catalog-scoping attributes — adding a second store is then a pure ImpEx addition, no Java
  change.
- When reviewing or designing anything touching `Product`, `Category`, CMS content, or prices: ask
  "does this assume exactly one catalog/store exists?" A lookup that only works today because
  there's one real store is a latent bug, not a simplification.

## Layers and Spring conventions

- **items.xml** — types, attributes, relations, enums, indexes. New item type → `deployment` with
  a unique typecode; new attribute → `<persistence type="property"/>` unless dynamic.
- **DAO** (`*Dao`/`Default*Dao`, extends `AbstractItemDao`) — FlexibleSearch only, parameterised
  queries, `{Type.attribute}` brace syntax with a parameter map, never string-concatenated input.
- **Service** (`*Service`/`Default*Service`) — business logic, transaction boundaries, models.
- **Facade** (`*Facade`/`Default*Facade`) — presentation-layer orchestration, DTOs.
- **Converter/Populator** — model→DTO. Extend via `modifyPopulatorList`, don't replace.
- **Controller** — thin, no business logic.
- Prefer extending/decorating platform behaviour (populator lists, interceptors, strategies,
  `parent=` overrides) over copying platform classes; if copied, say why.
- Beans in `resources/<ext>-spring.xml`; `parent=` to extend a platform bean, `<alias>` to take
  over an overridable name — that's what keeps customisations upgrade-safe. DTOs in
  `-beans.xml`, item types in `-items.xml`. Extension deps in `extensioninfo.xml`, and the
  extension listed in `config/localextensions.xml`.

## items.xml

- **Extending an existing item type** (e.g. adding custom attributes to `Product`) needs no
  `deployment`/typecode — those are only for genuinely new item types:
  ```xml
  <itemtype code="Product" autocreate="false" generate="false">
      <attributes>
          <attribute qualifier="myAttr" type="java.math.BigDecimal">
              <persistence type="property"/>
              <modifiers optional="true"/>
          </attribute>
      </attributes>
  </itemtype>
  ```
- **`<modifiers>` has no `mandatory` attribute.** This is not valid schema and will only fail at
  build time (`[schemavalidate] ... Attribute 'mandatory' is not allowed to appear in element
  'modifiers'`), not by inspection. To require an attribute, use `optional="false"`. This exact
  mistake shipped once in this codebase and only surfaced when `ant all` was actually run — "looks
  right" is not proof of a schema-valid items.xml; running the build is.
- **Do not add job-specific attributes to the generic `CronJob` type.** `CronJob` is the shared
  base type for every scheduled job in the entire installation (Solr indexers, cart removal, email
  cleanup, everything) — adding columns there pollutes every unrelated job, and a shared type
  cannot enforce `optional="false"` for only some of its rows. Use a dedicated subtype instead
  (`extends="CronJob"`), matching the platform's own convention (`SolrIndexerCronJob`,
  `CartRemovalCronJob`). `CronJob` does have a generic `sessionContextValues` (`java.util.Map`)
  attribute, but its documented purpose is seeding the session context the job runs under (paired
  with `sessionUser`/`sessionLanguage`/`sessionCurrency`) — not a general-purpose config bag, and
  no platform module uses it that way.
- **`CatalogVersion` reference attributes in ImpEx** use the composite-key syntax
  `catalogVersion(catalog(id),version)`, value format `catalogId:version` — e.g.
  `electronicsProductCatalog:Staged`. Verified against real usage in
  `bin/modules/adaptive-search/**` and `bin/modules/personalization/**` test ImpEx.
- An `items.xml`/`beans.xml` change needing a **system update** (`ant updatesystem` or HAC →
  Platform → Update) rewrites the schema. Per `CLAUDE.md`, `ant updatesystem` is pre-approved to run
  freely when verifying ImpEx/type-system wiring — but `ant initialize` (full DB wipe) is never run
  by an agent, always ask the user first.
- **New or changed custom ImpEx must be registered for automatic import, not just run once by
  hand** (HAC console, a one-off `ant importImpex -Dresource=...`). Wire it into the extension's
  `SystemSetup` class (`@SystemSetup(type = SystemSetup.Type.ESSENTIAL)` for core/reference data,
  `PROJECT` for sample/demo data), following the pattern already established for this project's
  cronjob ImpEx (see `CustomservicesSystemSetup.createProjectData()`). Both `ant initialize` and
  `ant updatesystem` invoke the same `SystemSetup` process — so verifying the wiring with
  `ant updatesystem` is sufficient proof it will also run under a future `ant initialize`, without
  ever needing to run that destructive command directly. An ImpEx file sitting under
  `resources/impex/` that nothing references from a `SystemSetup` class is exactly the kind of gap
  that only surfaces on a fresh install or a teammate's first `ant initialize` — not in the
  already-populated local DB an agent is testing against.

## CronJob / Job wiring

- **A Spring bean implementing `JobPerformable` is not auto-registered as a `Job` item.**
  `CronJob.job` is a reference that must resolve against an existing `Job` row. You must insert one
  explicitly:
  ```
  INSERT_UPDATE ServicelayerJob; code[unique=true]  ; springId
                                ; myJobBeanId        ; myJobBeanId

  INSERT_UPDATE MyCronJob; code[unique=true]  ; job(code)     ; singleExecutable; sessionLanguage(isocode)
                          ; myCronJobCode     ; myJobBeanId   ; true            ; en
  ```
  `springId` is what actually links the row to the Spring bean; `code` is the item's own
  identifier and is conventionally set to the same value. Verified against the platform's own
  `bin/modules/core-accelerator/acceleratorservices/resources/impex/essentialdata-acceleratorservices.impex`.
- **`sessionLanguage(isocode)` is required for a `CronJob` to actually run** — omitting it is easy
  to miss since it doesn't fail at ImpEx-import time, only when the job is triggered.
- **One `Job` Spring bean, many `CronJob` instances** is the standard pattern for anything that
  needs to run once per store/tenant/config (see "Multi-basestore" above) — matches the platform's
  own `SolrIndexerJob`/`SolrIndexerCronJob` (one bean, one `CronJob` row per `FacetSearchConfig`).
  The `Job` class reads all of its scoping from the `CronJobModel` instance `perform(...)` is
  handed, never from `ConfigurationService` or other shared/static state — that's what makes
  concurrent/overlapping runs across instances safe.
- `AbstractJobPerformable<T>.perform(T job)` returns `PerformResult(CronJobResult, CronJobStatus)`
  — not a `performCronJob` method (an easy wrong guess from other job-scheduling frameworks).

## ServiceLayer patterns

- **`ModelService.saveAll(Collection<?>)` exists** — batch it. `modelService.save(x)` called once
  per item inside a loop works but is an unnecessary persistence round-trip per item; collect the
  mutated models and call `saveAll(...)` once after the loop.
- **`ProductService.getProductForCode(CatalogVersionModel, String)`** (the two-argument overload)
  is preferred over the session-dependent single-argument `getProductForCode(String)` in any code
  that must not depend on session state — cronjobs, batch imports. Catch both
  `UnknownIdentifierException` (no match) and `AmbiguousIdentifierException` (more than one match)
  around the call.
- No cronjob `Job` class in the platform's own modules wraps `modelService.save()`/`saveAll()` in
  an explicit `Transaction`/`@Transactional` — each save call is its own ServiceLayer transaction;
  don't add explicit transaction wrapping unless there's a specific reason spelled out.

## Build and server lifecycle

- `JAVA_HOME` must point at Java 17 explicitly (machine default is often newer) — source it in the
  *same* shell command as any `ant`/`hybrisserver.sh` invocation, since shell state does not
  persist between tool calls:
  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 17); export PATH="$JAVA_HOME/bin:$PATH"
  ```
- `ant initialize` (full DB wipe) is never run by an agent — always ask first. `ant updatesystem`
  rewrites the schema too but is pre-approved to run freely (see "items.xml" above).
- `ant all` fails against an already-running server (JMX restart of a live instance) — check
  `ps aux | grep tomcat` first.
- **A JVM killed abruptly (force-killed, or an interrupted shutdown that never completes) can leave
  a stale HSQLDB lock file** (`data/hsqldb/*.lck`) that blocks every subsequent start attempt —
  the symptom looks exactly like "the server won't start" with no clear error. Before removing it,
  confirm no live process actually holds it (`ps aux | grep tomcat`, check the PID file); if none
  does, the lock is safe to delete.
- **No junit tenant configured** (`installed.tenants=` empty) means `ant unittests`/`ant alltests`
  do not work. Run tests directly:
  ```bash
  "$JAVA_HOME/bin/java" -cp "<ext>/classes:<ext>/resources:<platform+module classpath>" \
    org.junit.runner.JUnitCore com.your.package.YourTest
  ```
  Assemble the classpath from every extension's `classes`/`resources` dirs plus platform/module
  `lib` jars the extension depends on.

## Troubleshooting: intermittent startup hangs on this machine

Observed and diagnosed directly (not a documented platform issue, specific to this environment):
Tomcat starts webapp contexts (backoffice, hac, smartedit, storefront, mediaweb, …) in parallel via
its `Catalina-utility` thread pool. Under this installation's large extension count, that
parallelism can race Spring's CGLIB proxy generation of shared `@Configuration` classes (observed:
`org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration`) across
concurrently-initializing webapp classloaders — a genuine JVM-level class-initialization stall, not
a classic two-thread deadlock (`jstack`'s own deadlock detector does not report it, because the
contended lock is the JVM's internal class-init monitor, not a regular Java object monitor).

**Diagnosis**: take two `jstack <pid>` snapshots a few seconds apart. If a `BLOCKED`
`Catalina-utility-N` thread's `cpu=` time is byte-for-byte identical across both snapshots while
wall-clock time has moved, it is genuinely stuck, not just slow.

**What did *not* fix it**: switching the legacy `-Xdebug -Xnoagent -Xrunjdwp:...` debug flags to
the modern `-agentlib:jdwp=...` syntax. Tried and confirmed insufficient — the hang reproduced
identically with the modern flag active.

**Actual lever**: `tomcat.startStopThreads` (`bin/platform/project.properties`, default `0` = use
`Runtime.availableProcessors()`, i.e. full parallelism). Setting
`tomcat.startStopThreads=1` in `config/local.properties` makes Tomcat start webapps sequentially,
removing the race entirely — at the cost of a slower total boot. Like `tomcat.debugjavaoptions`,
this is templated into `server.xml` at build time, so it needs a rebuild to take effect after being
set. Not yet applied in this installation as of this writing — confirm current state before
assuming it's fixed.

## Review checklist additions

When reviewing hybris backend changes, beyond correctness/layering/upgrade-safety basics, check
specifically for the mistakes above: an items.xml change that wasn't actually built (schema
validity unverified), a `CronJob` without its `ServicelayerJob`/`sessionLanguage`, a per-item
`save()` that should be `saveAll()`, and any global-config assumption that would break under a
second `BaseStore`.
