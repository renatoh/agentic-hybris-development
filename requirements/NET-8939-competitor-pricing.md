# NET-8939 — Nightly competitor pricing import

| | |
|---|---|
| **Ticket** | NET-8939 |
| **Status** | Draft / not implemented |
| **Extension** | `customservices` |
| **Platform** | SAP Commerce Cloud 2211.37 |

## 0. Original request (verbatim)

> ok, let's work on the next requirement, NET-8939. we have a service where we receive pricing
> information from compeditor. we get an average competitor price, an min competitor price and a
> max competitor price. we get these prices per prodcut and want to safe it on the product. we need
> three new fields for that.
> we want to create a cronjob, which runs once a night (2am via cronexpression) with that we call an
> endpoint (we need a mock for it), from the endpoint we get a list with these 3 prices per product
> (product.code). we then lookup the product and save the 3 fields onto the product. please write a
> consice requirements.md

## 1. Context

A competitor pricing service supplies, per product, an average, a minimum and a maximum
competitor price. This data does not exist in Commerce today and needs three new fields on
`Product`. The source system is external and not available yet; a mock stands in until it is.

## 2. Goal

Once a night, a cronjob calls a REST endpoint that returns competitor pricing for every product it
knows about, looks up each product by `product.code`, and writes the three prices onto it.

- The endpoint URL is **not** hardcoded: it comes from the property file (same pattern as NET-8938).
- The real endpoint **does not exist yet**. A mock implementation returns the contents of a bundled
  JSON file. Switching from mock to real must not require a job/cronjob change.
- A product code the endpoint returns that does not resolve to a product is skipped and logged; it
  does not abort the run.
- **Multi-tenant**: this installation may run more than one store, each with its own product
  catalog and its own identity towards the external pricing system. The job must run **once per
  tenant/store**, each run scoped to its own tenant id and its own catalog version — not as one
  global run against a single hardcoded catalog (see §5.3).

## 3. Scope

**In scope**

- Three new attributes on the existing `Product` type (extend `Product` via `items.xml`, no new item
  type, no deployment/typecode).
- A Spring service in `customservices` that fetches competitor pricing for all products in one call.
- A mock implementation reading a bundled JSON file.
- A REST implementation skeleton reading the endpoint URL from configuration.
- A cronjob that runs the import once a night at 02:00, via a `CronJob`/`Trigger` with a
  `cronExpression`, with **one `CronJob` instance per tenant/store** sharing one `Job` implementation.

**Out of scope (unless decided otherwise — see §8)**

- Syncing the new attributes from the Staged to the Online catalog version.
- Any storefront/UI display of the competitor prices.
- Historization (price trend over time) — only the latest values are kept.
- Triggering a Solr reindex after the update.

## 4. Source data contract (mock)

The endpoint returns a flat JSON array, one entry per product:

```json
[
  {
    "productCode": "1934793",
    "averagePrice": 899.00,
    "minPrice": 849.50,
    "maxPrice": 949.90
  }
]
```

| Field | Type | Notes |
|---|---|---|
| `productCode` | string | Matches `Product.code` |
| `averagePrice` | number | Average competitor price |
| `minPrice` | number | Minimum competitor price |
| `maxPrice` | number | Maximum competitor price |

The mock file (`customservices/resources/competitorpricing/competitor_pricing_mock.json`) must use
product codes that actually exist in this installation's sample catalog (`electronicsProductCatalog`),
so the job's product lookup resolves for real during verification, not just against a fabricated code.

## 5. Design

### 5.1 New `Product` attributes

Extend the existing type in `customservices-items.xml` (new file — `customservices` has no
`items.xml` yet):

```xml
<itemtype code="Product" autocreate="false" generate="false">
    <attributes>
        <attribute qualifier="competitorAveragePrice" type="java.math.BigDecimal">
            <persistence type="property"/>
            <modifiers optional="true"/>
        </attribute>
        <attribute qualifier="competitorMinPrice" type="java.math.BigDecimal">
            <persistence type="property"/>
            <modifiers optional="true"/>
        </attribute>
        <attribute qualifier="competitorMaxPrice" type="java.math.BigDecimal">
            <persistence type="property"/>
            <modifiers optional="true"/>
        </attribute>
    </attributes>
</itemtype>
```

`BigDecimal` rather than the platform's own `PriceRow.price` (`Double`, `europe1-items.xml`) —
deliberate: these are stored facts pulled from an external system, not calculated storefront
prices, and should not pick up floating-point drift. Not localized: a price has no language.

Each attribute lives on the `ProductModel` instance for the catalog version it was written to (see
§5.3) — normal hybris attribute behaviour, not a cross-catalog-version value.

### 5.2 Service layer

Package `com.custom.competitorpricing` (sub-packages `service`, `dto`), mirroring the NET-8938
`orderhistory` package shape:

```
CompetitorPricingService                (interface)
 └─ getCompetitorPricing(String tenantId) : List<CompetitorPriceDto>

MockCompetitorPricingService            reads the bundled JSON from the classpath
RestCompetitorPricingService            calls the configured REST endpoint
```

- `tenantId` identifies which store/tenant's competitor pricing to fetch from the external system —
  it is **not** a hybris concept (not a `BaseStore.uid`), it is whatever identifier the external
  pricing system itself uses for the tenant; it is supplied per `CronJob` instance (§5.3), never
  hardcoded or read from global configuration.
- `CompetitorPriceDto` (`productCode`, `averagePrice`, `minPrice`, `maxPrice`) is a plain Jackson
  POJO in `src/`, not a `-beans.xml` type.
- The mock reads `/competitorpricing/competitor_pricing_mock.json` from the classpath, **regardless
  of the `tenantId` argument** — there is only one bundled payload; a real multi-tenant mock is not
  needed to prove the design (this installation has exactly one real store/catalog to test against,
  see §4). The REST implementation passes `tenantId` to the endpoint (e.g. as a query parameter or
  path segment — implementer's choice, document whichever is picked).
- The REST implementation uses its own `RestTemplate` bean (`competitorPricingRestTemplate`,
  own request factory) — a separate instance from `customOrderHistoryRestTemplate` (NET-8938), one
  `RestTemplate` per integration, matching the existing file's own pattern.
- Any transport/parse failure throws a dedicated `CompetitorPricingException`; the service never
  returns a partial or empty list silently.

Bean wiring in `customservices-spring.xml` (appended to the existing file):

```xml
<alias name="mockCompetitorPricingService" alias="competitorPricingService"/>
```

Switching to the real endpoint = changing that alias to `restCompetitorPricingService`. No Java
change, no job change.

### 5.3 CronJob

**One `Job` implementation (one Spring bean), one `CronJob` instance per tenant/store** — the same
pattern the platform itself uses for e.g. `SolrIndexerCronJob` (one `SolrIndexerJob` bean, one
`SolrIndexerCronJob` row per `FacetSearchConfig`). This is a design decision made explicitly to keep
the solution multi-tenant capable: a property-file-based single catalog (an earlier draft of this
spec) does not work once more than one store/catalog exists.

```
CompetitorPricingImportJob extends AbstractJobPerformable<CompetitorPricingImportCronJobModel>
    perform(CompetitorPricingImportCronJobModel job) : PerformResult
```

- `items.xml`: a dedicated `CompetitorPricingImportCronJob extends CronJob` subtype (`autocreate=
  "true" generate="true"`), carrying the two attributes that make one instance tenant-specific:

  | Attribute | Type | Notes |
  |---|---|---|
  | `tenantId` | `java.lang.String` | Mandatory. Passed straight through to `CompetitorPricingService.getCompetitorPricing(tenantId)` — identifies the tenant towards the *external* system, not a hybris concept. |
  | `catalogVersion` | `CatalogVersion` | Mandatory. The exact catalog version this instance writes competitor prices into. |

  These attributes live on **our own subtype**, not on the generic `CronJob` type: `CronJob` is the
  shared base type for every cronjob in the whole installation (Solr indexers, cart removal, email
  cleanup, everything), so adding tenant-specific columns there would pollute every unrelated job
  with attributes it never uses, and a shared type cannot enforce `mandatory="true"` for only some
  of its rows. `CronJob` does already carry a generic `sessionContextValues` (`java.util.Map`)
  attribute, but its documented purpose is seeding the session context the job runs under (paired
  with `sessionUser`/`sessionLanguage`/`sessionCurrency`), not arbitrary job-specific business
  config, and no platform module uses it that way — a typed attribute on a dedicated subtype is the
  idiomatic and only precedented approach in this codebase (confirmed against the platform's own
  `SolrIndexerCronJob`, `CartRemovalCronJob`).
- Spring bean: `parent="abstractJobPerformable"`. One bean for all tenants — `perform(...)` takes
  its scoping entirely from the `CompetitorPricingImportCronJobModel` instance it is handed at
  runtime, never from `ConfigurationService` or any other global/shared state. This is what makes
  the one-bean-many-instances pattern safe under concurrent or overlapping runs across tenants.
- `perform(job)`:
  1. Read `job.getTenantId()` and `job.getCatalogVersion()` off the instance.
  2. Call `competitorPricingService.getCompetitorPricing(job.getTenantId())`.
  3. For each `CompetitorPriceDto`: resolve the product via `ProductService.getProductForCode(
     CatalogVersionModel, String)` — the two-argument overload, using `job.getCatalogVersion()`
     directly (already resolved, no `CatalogVersionService` lookup needed at runtime) — not the
     session-dependent single-argument one, since a cronjob must not depend on session state.
  4. If no product matches the code (or the code resolves ambiguously — see §7): log a WARN naming
     the tenant, catalog version and code, and skip it; continue with the rest.
  5. On a match: set the three attributes and `modelService.save(product)`. No explicit
     `Transaction`/`@Transactional` wrapping — no cronjob `Job` class in the platform's own modules
     does that; each `save()` is its own ServiceLayer transaction.
  6. If `getCompetitorPricing(tenantId)` itself throws (endpoint unreachable or broken for that
     tenant): log the error, return `PerformResult(CronJobResult.ERROR, CronJobStatus.FINISHED)` for
     **that instance only** — a failure for one tenant must not affect any other tenant's `CronJob`
     instance or run. No partial run is attempted within one instance either — either the full list
     for that tenant is fetched, or none of it is.
  7. On success: `PerformResult(CronJobResult.SUCCESS, CronJobStatus.FINISHED)`.

ImpEx (new file, `resources/impex/customservices-competitorpricing-cronjob.impex`) — one `CronJob` +
`Trigger` row per tenant/store, all referencing the same `Job`. This installation has exactly one
real store (`electronics`), so one instance is created; the pattern is what proves multi-tenant
readiness, not a second store that doesn't otherwise exist here:

```
INSERT_UPDATE CompetitorPricingImportCronJob; code[unique=true]                          ; job(code)                    ; singleExecutable; tenantId    ; catalogVersion(catalog(id),version)
                                             ; competitorPricingImportCronJob_electronics ; competitorPricingImportJob   ; true            ; electronics ; electronicsProductCatalog:Staged

INSERT_UPDATE Trigger; cronjob(code)[unique=true]                    ; cronExpression
                      ; competitorPricingImportCronJob_electronics    ; 0 0 2 * * ?
```

Adding a second store later is a **pure ImpEx addition** — one more `CronJob` + `Trigger` pair with
its own `code`, `tenantId` and `catalogVersion` — no Java change, no new `Job` bean.

Imported as project data (`customservices.import.projectdata.n=...`) — a cronjob/trigger is
environment-active project data, not the kind of reference data that belongs in essential data.

### 5.4 Configuration

Defaults in `customservices/project.properties`:

| Property | Default | Purpose |
|---|---|---|
| `customservices.competitorpricing.rest.endpoint.url` | *(empty)* | Base URL of the external competitor pricing endpoint |
| `customservices.competitorpricing.rest.connect.timeout.ms` | `5000` | Connect timeout |
| `customservices.competitorpricing.rest.read.timeout.ms` | `10000` | Read timeout |
| `customservices.competitorpricing.mock.resource` | `/competitorpricing/competitor_pricing_mock.json` | Classpath location used by the mock |

Read via `ConfigurationService`, resolved per job run (not cached), so the endpoint can be switched
in HAC without a restart. **No catalog/tenant property exists here** — that would not survive a
second store; `tenantId` and `catalogVersion` are per-`CronJob`-instance attributes (§5.3), not
global configuration.

## 6. Acceptance criteria

1. `Product` has three new persisted attributes: `competitorAveragePrice`, `competitorMinPrice`,
   `competitorMaxPrice` — visible in HAC's type system browser after a system update.
2. A manual run of `competitorPricingImportCronJob_electronics` (via HAC → System → Maintenance →
   CronJobs, or `hac`'s "Execute now") reads the mock file and writes all three values onto every
   product whose code matches, in that instance's `catalogVersion`.
3. A product code from the payload that does not exist in the catalog is skipped: the run still
   finishes with `SUCCESS`, and a WARN is logged naming the unresolved code.
4. `customservices.competitorpricing.rest.endpoint.url` exists in `project.properties`, is readable
   in HAC, and is the only place the endpoint URL is defined — no URL literal in the Java code.
5. Switching the Spring alias from the mock to the REST implementation requires no Java change and
   no ImpEx change.
6. The REST implementation, pointed at an unreachable URL, produces a logged error and the cronjob
   finishes with `CronJobResult.ERROR` — it must not partially update some products and not others
   from a failed fetch, and must not throw an uncaught exception out of `perform(...)`.
7. The `Trigger` fires the job at 02:00 daily (`cronExpression = 0 0 2 * * ?`), verified by
   inspecting the trigger in HAC, not only by reading the ImpEx.
8. Unit tests in `customservices/testsrc` cover: JSON deserialization of the mock file, the
   per-product update including the skip-and-continue behaviour for an unresolved code, that a
   `CompetitorPricingException` from the service results in `CronJobResult.ERROR` without any
   `modelService.save()` calls having been made for that run, and that `tenantId`/`catalogVersion`
   are read from the `CompetitorPricingImportCronJobModel` instance the job is given (not from any
   shared/static/config source).
9. `ant clean all` succeeds; no changes under `bin/platform` or `bin/modules`.
10. Adding a second tenant/store requires **only** a new `CompetitorPricingImportCronJob` + `Trigger`
    ImpEx row (its own `tenantId` and `catalogVersion`) — no Java change, no second `Job` bean, and
    running it does not read, write, or otherwise affect the first tenant's products.
11. `tenantId` and `catalogVersion` are `mandatory` on `CompetitorPricingImportCronJob` — creating an
    instance without either fails at the type-system level, not silently at runtime.

## 7. Open questions

1. **Product identification** — `getProductForCode(catalogVersion, code)` is scoped to one
   `catalogVersion` per §5.3, so a code is only ever resolved within its own tenant's catalog — a
   code colliding *across two different tenants' catalogs* is not a concern by construction. Within
   one catalog version, `Product.code` uniqueness is a platform guarantee, not something this job
   re-checks.
2. **Mapping tenantId to a real store** — §5.3's ImpEx sets `tenantId=electronics` by convention
   (matching the `BaseStore`/catalog name), but nothing enforces that `tenantId` matches any hybris
   identifier — it is only ever passed straight through to the external system. Who decides the
   actual tenant identifiers when a second store is onboarded, and where is that documented, so the
   ImpEx for a new `CronJob` instance uses the value the external system actually expects?
3. **Staged vs. Online** — the import writes to each instance's configured `catalogVersion` (Staged
   for `electronics` today). Does the business need these three fields synced to `Online`
   automatically, or is a manual/scheduled catalog sync (existing platform mechanism) an acceptable
   follow-up step, out of scope here?
4. **Missing/negative/non-numeric values** — the mock always supplies all three fields as positive
   numbers. Real endpoint data quality (nulls, negative prices, min > max) is unconfirmed; for now a
   missing field is left `null` on the product and logged at WARN, same tolerance as an unresolved
   product code.
5. **Reindexing** — should a successful run trigger (or schedule) a Solr full/partial index update
   so the new prices are searchable/filterable, or is that a separate, manually-triggered concern?
6. **Volume** — the mock is a handful of products. Is the real feed close to the full catalog size
   (tens of thousands of products)? If so, `getCompetitorPricing(tenantId)` returning one in-memory
   `List` and `modelService.save()` per product one-by-one may need paging/batching per tenant — not
   addressed here.

## 8. Files expected to change

```
bin/custom/customservices/resources/customservices-items.xml                      (new — Product extension)
bin/custom/customservices/project.properties                                      (new properties)
bin/custom/customservices/resources/customservices-spring.xml                     (beans + alias, cronjob bean)
bin/custom/customservices/resources/impex/customservices-competitorpricing-cronjob.impex  (new — CronJob + Trigger)
bin/custom/customservices/src/com/custom/competitorpricing/**                     (service, dto, job)
bin/custom/customservices/testsrc/com/custom/competitorpricing/**                 (unit tests)
bin/custom/customservices/resources/competitorpricing/competitor_pricing_mock.json (new mock payload)
```
