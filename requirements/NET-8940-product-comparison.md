# NET-8940 — Product comparison lists

| | |
|---|---|
| **Ticket** | NET-8940 |
| **Status** | Draft / not implemented — needs agreement before implementation |
| **Extension** | `customstorefront` (web layer) + `customservices` (service/config) |
| **Platform** | SAP Commerce Cloud 2211.37, storefront `customstorefront` (B2C) |

## 0. Original request (verbatim)

> next feature, NET-8940, we want to compare similar products, e.g. two cameras. I want to be able
> to add the camera to the comparison list on the PDP. we only put the products to the same
> comparison list if they are in the same L2 category, like Cameras/Digital Cameras. if a product
> from a different L2 category is added we create a new product comparison list. in the top right
> corner, next to the cart, we will have an new icon comparison list. which takes us to the new
> product-comparison page. there we show all the products from the same comparison list side by
> side and have a drop down to switch between the lists

## 1. Context

Shoppers browsing similar products (e.g. two cameras) want to compare them side by side. Today
there is no comparison feature in the storefront.

## 2. Goal

A shopper can add a product to a comparison list from the PDP. Products only ever share a list if
they belong to the same **grouping category** — a configurable depth in the category tree, default
**depth 3 ("L3")**, e.g. two products under `Cameras/Digital Cameras/Digital Compacts` go into the
same list; a product under a different category at that depth (`Cameras/Digital Cameras/Digital SLR`,
or an unrelated top-level category) starts a new list. Depth 2 ("L2", `Digital Cameras`) was tried
first but proved too broad in practice — it merged an actual camera with an unrelated lens
accessory filed several levels under the same L2 node (see §8.5) — so depth 3 is the default.
**The depth is a property, not a hardcoded level** — changing grouping to L2, L4, or any other depth
is a config change, not a code change (see §5.2). A header icon next to
the cart links to a comparison page that shows one list at a time, side by side, with a dropdown to
switch between the shopper's current lists.

- **Session-scoped only** — comparison lists live in the HTTP session, like a guest cart. Nothing
  is persisted, no login/logout survival, no new persistent item type.
- **No cap** on products per list — the comparison table gets one column per product and scrolls
  horizontally.
- **PDP is the only entry point** — no "add to compare" on PLP/search result tiles.
- Comparison rows are the **classification attributes** assigned to the list's L2 category (via
  the existing hybris Classification System), not a hardcoded attribute set — this scopes
  naturally per category and needs no per-feature configuration.

Because this is session state resolved from the request's own store/catalog context (the
shopper's active session already belongs to exactly one store), it does **not** trigger the
multi-basestore "don't resolve current store implicitly" concern from the `sap-commerce-cloud`
skill — that concern is about code with no request/session to resolve from (cronjobs, batch
imports). It still must not assume there is only one catalog in the system: L2-category lookup
and classification-attribute lookup must go through the session's own `CatalogVersionModel`,
never a hardcoded catalog id.

## 3. Scope

**In scope**

- "Add to compare" action on the PDP.
- Session-held comparison lists, grouped by L2 category, no persistence, no cap.
- Header icon (next to the minicart) linking to the comparison page.
- Comparison page: one list shown at a time (columns = products, rows = classification
  attributes for that list's L2 category, plus name/image/price), with a dropdown to switch lists.
- Remove-from-list action on the comparison page; a list that drops to zero products disappears.

**Out of scope (unless decided otherwise — see §8)**

- Persistence across sessions/devices, anonymous→customer merge on login.
- Add-to-compare from PLP/search results.
- A cap/limit on comparison list size.
- B2B storefront (`yb2bacceleratorstorefront` is commented out; not in scope).
- Any REST/OCC exposure of comparison lists.

## 4. Definitions

**Grouping category** — the category at a configurable depth in the category tree, counting **from
the first real navigation category below the catalog's synthetic root, which is not itself
counted** (so depth 1 = the top-level nav category, e.g. `Cameras`; depth 2 = "L2", e.g.
`Digital Cameras` under `Cameras`; depth 3 = "L3", one level deeper). The catalog root (`electronicsProductCatalog`'s
category `1`) is scaffolding, not a real category, and must be excluded from the counted path —
including it shifts every depth value one level too shallow (verified bug: with the root counted,
depth 2 resolved to `Cameras` instead of `Digital Cameras`, e.g. grouping a camera and an unrelated
lens accessory together — see §8.5). The depth comes from a `project.properties` default
(`customservices.productcomparison.grouping.category.depth=3`), read via
`configurationService.getConfiguration().getInt(...)` — never hardcoded as "the second category" in
code. Resolved along the path from a product's assigned category up to its root (reusing the
platform's `ProductAndCategoryHelper`/`getSupercategories()` walk, the way the breadcrumb builder
does — see §5.2). A product can carry several assigned categories/paths; the first path that
resolves a category at the configured depth is used.

This assumes the demo catalog's navigation categories form a single-parent chain from each leaf to
one root — **verified** against `electronicsProductCatalog`'s real category ImpEx (see §8.1).

## 5. Design

### 5.1 Session model

A `ProductComparisonService` (new, in `customservices` or the addon, whichever the backend agent's
package layout favours) holds comparison state in the session via `SessionService`, keyed by a
single session attribute holding a list of comparison lists:

- Each comparison list: L2 `CategoryModel` (or its code + owning `CatalogVersionModel`) + an
  ordered `List<ProductModel>` (or product codes, resolved lazily).
- `addProduct(ProductModel)` — resolves the product's L2 category; if a list for that category
  already exists in session, appends to it (no duplicate product in the same list); otherwise
  creates a new list.
- `getLists()` — all current lists for the session.
- `removeProduct(listId, ProductModel)` — removes a product; deletes the list if it becomes empty.
- No DB persistence, no new item type — this is pure session/service-layer state.

### 5.2 Grouping category resolution

A small helper (or a method on `ProductComparisonService`) that, given a `ProductModel`:

1. Picks one of the product's assigned categories and walks `getSupercategories()` upward (reusing
   the platform's `ProductAndCategoryHelper` the way `ProductBreadcrumbBuilder` does) to build the
   full path from that category to the root.
2. Reads the configured depth (`customservices.productcomparison.grouping.category.depth`, default
   `3`) via `ConfigurationService` — never a literal depth value in the resolution code.
3. Returns the `CategoryModel` at that depth along the path (root = depth 1).

Two products share a list only if this resolves to the **same** `CategoryModel` instance (by PK),
not just the same name/code (matters once more than one catalog exists). Changing the depth
property from `2` to `3` regroups by L3 with no code change — this is the acceptance test for
"not hardcoded" (§6 AC12). A path shorter than the configured depth (a product filed only under a
top-level category) has no grouping category at that depth; §8.4 covers what happens then.

### 5.3 Classification-based comparison rows

For a given list's grouping category, use the existing `ClassificationService` to find the
**full** `ClassAttributeAssignment` set for that category's classification system version — not
inferred from the union of what the currently-listed products individually carry, which under-
resolves the category's actual attribute set (real bug, found and fixed once already: see §8.6).
For each product in the list, read its `ClassificationService.getFeatures(...)` for those
assignments.

Two-step row construction, both steps required:
1. **Resolve candidate rows** from the category's full classification assignment set (per above) —
   this is the correctness fix, and it must not be reverted or bypassed.
2. **Filter for display**: drop any candidate row where *every* product in the currently displayed
   list has a blank/missing value — this is the explicit UX requirement (a row with at least one
   non-empty value across the products still shows, with empty cells only for the products actually
   missing that value; that part is unaffected). This filter is applied *after* step 1, on the full
   candidate set, not as a substitute for step 1's resolution.

Getting this wrong once already broke the feature two different ways: resolving rows narrowly (step
1 done via the union-of-product-values shortcut) silently drops genuinely-assigned attributes with
no current values instead of just hiding empty rows (§8.6); dropping step 2 entirely brings back
rows that are empty for every product in the list, which is a direct regression against this
requirement. Both steps are permanent, not alternatives.

### 5.3a Configuration

| Property | Default | Meaning |
|---|---|---|
| `customservices.productcomparison.grouping.category.depth` | `3` | Category-tree depth (root excluded, first real nav category = depth 1) that defines a grouping category. Change to `2` to group more broadly (L2) or `4` more narrowly — no code change. |

Default lives in `customservices/project.properties`; an environment override belongs in
`config/local.properties` (operator-set, not edited by an agent — see `CLAUDE.md`).

### 5.4 Web layer

Implemented in the project's own cloned storefront extension (not `bin/modules`). New pieces:
- `AddToCompareController` — PDP form/AJAX action, `POST`, product code in, redirects/refreshes the
  PDP fragment showing the icon count.
- `ProductComparisonPageController` — renders the comparison page; accepts an optional list
  selector (e.g. query param) and defaults to the most recently touched list.
- Header icon: a JSP fragment/tag added next to the existing minicart include, showing a count
  (total lists, or total products — see §8) and linking to the comparison page. Hidden or disabled
  when there are no lists. Use this SVG (agreed asset, inline in the JSP or as a static resource
  under the storefront's `web/webroot/_ui/.../images`, matching how the minicart icon is served):
  ```html
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 384 512" width="24" height="24" alt="a"><path fill="currentColor" d="M145.5 68c5.3-20.7 24.1-36 46.5-36s41.2 15.3 46.5 36l3.1 12H288v48H96V80h46.4zM192 0c-32.8 0-61 19.8-73.3 48H64v32H0v432h384V80h-64V48h-54.7C253 19.8 224.8 0 192 0m128 144v-32h32v368H32V112h32v48h256zM208 80a16 16 0 1 0-32 0 16 16 0 1 0 32 0m-72 192a24 24 0 1 0-48 0 24 24 0 1 0 48 0m40-16h-16v32h128v-32H176m0 96h-16v32h128v-32H176m-64 40a24 24 0 1 0 0-48 24 24 0 1 0 0 48"></path></svg>
  ```
- Comparison page JSP: dropdown populated from `getLists()` (label = grouping category name), table
  as described in §5.3.

## 6. Acceptance criteria

1. Adding a product to compare from the PDP creates a comparison list if none exists yet for that
   product's grouping category.
2. Adding a second product from the same grouping category appends to the existing list, not a new
   one.
3. Adding a product from a different grouping category creates a second, independent list.
4. Adding the same product twice to the same list does not duplicate it.
5. The header icon appears next to the cart icon, is hidden/disabled with zero lists, and links to
   the comparison page.
6. The comparison page shows one list at a time as a table: one column per product, one row per
   classification attribute assigned to that list's grouping category, plus name/image/price.
7. A dropdown on the comparison page switches between all of the session's current lists.
8. Removing a product from a list removes its column; removing the last product removes the list
   entirely (and the dropdown/icon reflect that).
9. Nothing is persisted — a new session (or session invalidation) starts with zero lists.
10. Category and classification lookups go through the session's own `CatalogVersionModel`, never
    a hardcoded catalog id (multi-store safe, see §2).
11. B2B storefront is untouched.
12. **Changing `customservices.productcomparison.grouping.category.depth` from `3` to `2` regroups
    products by L2 with no code change** — proven by a unit test that resolves the same product's
    grouping category under both depth values and asserts a different `CategoryModel` PK.

## 7. Non-goals

- Cross-session/device persistence.
- PLP/search-result add-to-compare.
- A size cap.
- Comparison-list analytics/tracking.

## 8. Open questions

1. ~~L2 resolution on the actual demo catalog~~ — **verified**: `electronicsProductCatalog`'s
   sample data (`categories.impex`/`categories_en.impex`) confirms a single-parent chain, e.g.
   category `575` "Digital Cameras" → supercategory `571` "Cameras" → supercategory `1` (root),
   exactly matching §4's assumption and the `Cameras/Digital Cameras` example. Classification data
   also confirmed present (`ElectronicsClassification:1.0`, e.g. class `1094` "Lens system"),
   grounding §5.3.
2. ~~Icon badge content~~ — **decided**: total count of products across all lists, not count of
   lists.
3. **Default list on landing on the comparison page** — most-recently-added-to list, unless you'd
   rather it be the first list, or require an explicit selection.
4. **A product whose category path is shallower than the configured depth** (e.g. depth set to `3`
   but a product is only filed under an L1/L2 path) — no grouping category resolves. Default
   behaviour: skip adding it and show a message on the PDP ("not available for comparison"), unless
   you'd rather it fall back to the deepest category actually available on its path.
5. ~~Root-counting off-by-one~~ — **found and fixed**: manual testing showed a camera
   (`DSC-N1`, under `Cameras/Digital Cameras/Digital Compacts`) and an unrelated lens accessory
   (`NP03ZL`, under `Cameras/Digital Cameras/Digital SLR/Camera Lenses`) landing in the same
   comparison list at the default depth `2`. Cause: `buildPathFromRoot()` included the catalog's
   synthetic root category (code `1`) in the counted path, shifting every depth value one level too
   shallow — depth `2` resolved to `Cameras` instead of `Digital Cameras`. Fixed by excluding the
   root from the counted path (§4).
6. ~~Classification-row resolution/filtering got tangled twice~~ — **found and fixed**: a code
   review correctly flagged that resolving row candidates from the union of what listed products
   happen to carry (instead of the category's full classification assignment set) silently drops
   genuinely-assigned attributes with no current values — a real spec deviation. Fixing that,
   though, dropped the *separate* empty-row-filter step entirely, bringing back rows blank for every
   product — a direct regression against this doc's own requirement. Both are needed together, not
   as alternatives: resolve from the full set, then filter empty-for-all as a second step (§5.3).
