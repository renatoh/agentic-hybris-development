# NET-8943 — Sell services (warranty extension, installation) with products in the cart

| | |
|---|---|
| **Ticket** | NET-8943 |
| **Status** | Draft — needs agreement before implementation |
| **Extension** | `customcore` (item types, enums, ImpEx) + `customservices` (price hook, cart linkage) + `customfacades` (facade/DTOs) + `customstorefront` (cart page) + `custombackoffice` (editor config) |
| **Platform** | SAP Commerce Cloud 2211.37, storefront `customstorefront` (B2C) |

## 0. Original request (verbatim)

> we want to sell serviced on top of the products, such as service could be warranty extension to 3
> years, or installation. the customer can then in the cart see and select different services he
> wants to add to the product

Decisions from the follow-up conversation (verbatim where quoted):

> we can't do fix price, since a warranty extension for a cheap prodcut can't cost the same as for
> an expensive product, same for installation, the price heavily depends on the product,
> servicec can be selected only on the cart page

> not every product has a different price. the service should have one price per price condition,
> then the product will have a servicePriceCondition field, which we use to pick the correct price.
> we can just introduce some dummy price condition (low, medium, high) for now

> we still want to have an own product type for services and link them to the 'normal' product. but
> the service product will be shared across all products and have different price rows

- **Pricing**: each service has **one price per price condition** (`LOW` / `MEDIUM` / `HIGH` for
  now). A product's `servicePriceCondition` selects which of those prices applies. Prices are
  **standard `PriceRow`s on out-of-the-box product price groups** (§4.4), with no new price type and
  no custom price matching.
- **One condition per product** applies to all of that product's services. Confirmed as enough for
  now.
- **Service products are shared**: one `SVC_INSTALLATION` product for every product it can be
  installed on, carrying several price rows.
- **Services are linked explicitly** to the normal products they're offered for.
- **Quantity**: a service's quantity always equals its product's quantity.
- **Where**: services can be selected **on the cart page only**.

Ruled out during design, recorded so they aren't revisited:

- A price stored per product–service pair in a custom table: too much data, and it sits outside the
  price engine.
- One variant product per product–service pair: tens of thousands of variants under one base
  product.
- A `PriceRow` subtype (or a new `PriceRow` attribute) carrying the condition: the standard price
  lookup can't select by such a column, so the hook would have to reimplement Europe1's row ranking
  (customer price group, dates, minimum quantity, net/gross). Product price groups (§4.4) already
  express "a price for a class of products" and are matched by the platform.
- **Option B**, one variant per service × condition (`SVC_INSTALLATION_LOW/_MEDIUM/_HIGH`), chosen
  when the service is ticked. It would have kept price calculation free of custom code, but it
  splits each service into several products. **The user chose option A**: one shared service product
  per service, with the condition resolved at price-calculation time (§5.2). That accepts the
  replaced platform price strategy described there.

## 1. Context

The shop sells only physical products today. Margin-rich add-ons such as an extended warranty or
professional installation can't be sold together with the product they apply to.

## 2. Goal

On the cart page, each cart line whose product offers services shows them as options. For example,
for a dishwasher marked `MEDIUM`: "Installation, 120.00" and "3-year warranty extension, 79.00".
Ticking an option adds the service to the order for that cart line, and unticking removes it.

**A selected service is its own cart entry** for the shared service product, priced through the
platform's price engine. It is *not* added to the price of the product it belongs to:

| Cart entry | Product | Base price from | Example |
|---|---|---|---|
| #0 | Dishwasher `DW-500` (condition `MEDIUM`) | its own normal `PriceRow` | 699.00 |
| #1 | `SVC_INSTALLATION` | price row for product price group `SVC_INSTALLATION_MEDIUM` (§4.4, §5.2) | 120.00 |
| | | **Cart total** | **819.00** |

In the cart, order and confirmations, the service is shown under the product it was bought for and
stays tied to it (§5.3, §5.7).

### 2.1 Platform building blocks reused (verified)

- **`ProductReference`** (`catalog`): already links a product to other products by
  `referenceType`, is catalog-version-aware, and is maintained in Backoffice. **Reused** to link a
  product to its services, with a new reference type `SERVICE` (§4.3).
- **`PriceRow` with product price groups** (`europe1`, `PriceRow.pg` → dynamic `ProductPriceGroup`
  enum): Europe1's own "price for a class of products". **Reused unchanged** for service prices
  (§4.4). The service-layer price criteria accept a product price group explicitly
  (`DefaultPriceValueInfoCriteria.Builder.withProductPriceGroup(...)`, `platformservices`), so
  currency, customer price group, validity dates, minimum quantity and net/gross are all matched by
  the platform.
- **`FindPriceHook`** (`platformservices`): the official extension point for overriding a cart
  entry's **base price**. It's wired as the `findPriceHooks` list in
  `platformservices/resources/order-spring.xml` and called by `DefaultSLFindPriceStrategy`. SAP's
  own `subscriptionservices` (`SubscriptionPriceFindPriceHook`) and `sapproductconfigservices`
  (`DefaultProductConfigFindPriceHook`) use it. **Reused** (§5.2). It only selects the base price,
  so discounts, promotions and taxes run after it unchanged. **Limitation found during
  implementation:** `DefaultSLFindPriceStrategy` runs its default lookup *before* the hooks, and that
  lookup throws for a product with no matching row. SAP's own hook users never hit this, because
  their products always have a default price. That's why §5.2 replaces the strategy with a small
  subclass.
- **Entry groups** (`AbstractOrderEntry.entryGroupNumbers`, `AbstractOrder.entryGroups`, dynamic
  `GroupType` enum): the platform's own way of tying cart entries together. **Reused** (§5.3).
- **Configurable bundles** (`configurablebundleservices`, installed): checked and **not used**. They
  provide a guided "build your bundle" flow from a bundle template, with the storefront addon
  deprecated in 2211, which is a different problem.

## 3. Scope

**In scope**

- A `ServiceProduct` type and two shared service products: 3-year warranty extension and
  installation.
- A `ServicePriceCondition` enum (`LOW`, `MEDIUM`, `HIGH`) and a `servicePriceCondition` attribute on
  `Product`.
- Service prices as standard `PriceRow`s on one `ProductPriceGroup` per service × condition, per
  currency.
- Linking products to the services they offer (`ProductReference`, type `SERVICE`).
- Showing available services per cart line on the cart page with the applicable price, and
  selecting or deselecting them there.
- Pricing service entries through a `FindPriceHook`, so discounts, promotions and taxes work as for
  any product.
- Keeping a service's quantity equal to its product's quantity. Removing the product removes its
  services.
- Showing services under their product in the cart, checkout summary, order confirmation, order
  history and confirmation email.
- Backoffice maintenance of all of the above.

**Out of scope**

- Selecting services anywhere other than the cart page, such as the product page, quick view,
  minicart or checkout steps.
- A different price condition per service for the same product. One condition per product is
  enough for now.
- Fulfilling the service after the order: warranty registration, installation scheduling.
- Selling a service on its own, without a product.
- Showing services in OCC/REST APIs.
- The B2B storefront, which is commented out.
- Localization beyond English.

## 4. Data model (all in `customcore-items.xml`)

### 4.1 `ServicePriceCondition` (enum)

```xml
<enumtype code="ServicePriceCondition" autocreate="true" generate="true" dynamic="true">
    <value code="LOW"/>
    <value code="MEDIUM"/>
    <value code="HIGH"/>
</enumtype>
```

It's **dynamic**, so merchandisers can add more conditions later through Backoffice or ImpEx
without a code change or rebuild. The three values are placeholders agreed for now.

### 4.2 `Product.servicePriceCondition` and `ServiceProduct`

```xml
<itemtype code="Product" autocreate="false" generate="false">
    <attributes>
        <attribute qualifier="servicePriceCondition" type="ServicePriceCondition">
            <persistence type="property"/>
            <modifiers optional="true"/>
        </attribute>
    </attributes>
</itemtype>

<itemtype code="ServiceProduct" extends="Product" autocreate="true" generate="true"
          jaloclass="com.custom.core.jalo.ServiceProduct"/>
```

- `servicePriceCondition` is optional. A product **without** a condition offers no services, even
  if service references exist. The cart page skips it, and the hook never prices a service for it.
- `ServiceProduct` has **no new attributes**. It's a subtype so that code and queries can reliably
  tell a service apart from a physical product. A product subtype with no own attributes needs no
  `deployment`. The implementer verifies this against how `ApparelProduct`/`ElectronicsColorVariantProduct`
  are already declared in `customcore-items.xml`.
- **One shared product per service**: `SVC_INSTALLATION` and `SVC_WARRANTY_3Y`. Name and
  description (English) are what the cart shows. Each service product carries its tax group.
- Service products must never appear as standalone products: they're excluded from the Solr index,
  have no reachable product page (404), and are rejected by the normal add-to-cart endpoints
  (`/cart/add` and the like).

### 4.3 Linking products to services: `ProductReference` type `SERVICE`

Add the value `SERVICE` to `ProductReferenceTypeEnum`:

```xml
<enumtype code="ProductReferenceTypeEnum" autocreate="false" generate="false">
    <value code="SERVICE"/>
</enumtype>
```

- `ProductReferenceTypeEnum` (`catalog-items.xml`) is **not dynamic**, so this value is added at
  build time. Prove that this works with an actual `ant all` plus `ant updatesystem` before building
  on it. If the build rejects it, stop and report. Don't switch to a custom relation silently.
- A product offers service S if and only if an **active** `ProductReference` exists with
  `source` = the product, `target` = S (a `ServiceProduct`), and `referenceType` = `SERVICE`.
- `ProductReference` is catalog-aware through its source product and syncs Staged→Online with it.
  The implementer verifies that references of the new type are included in the existing
  electronics catalog sync.
- Display order of a product's services in the cart follows the references' order on the product.
- Accelerator components that render product references, such as
  `ProductReferencesComponent` on the PDP, must **not** start showing services. Check every place
  that lists references by type, and confirm none of them pick up all types.

### 4.4 Service prices: standard `PriceRow`s on product price groups (no type changes)

`PriceRow` is **not** subtyped or extended. Service prices use Europe1's out-of-the-box product
price groups:

- One `ProductPriceGroup` value (a dynamic enum, `europe1-items.xml`) per **service × condition**,
  with code `<serviceCode>_<conditionCode>`, e.g. `SVC_INSTALLATION_LOW`,
  `SVC_INSTALLATION_MEDIUM`, `SVC_INSTALLATION_HIGH`, `SVC_WARRANTY_3Y_LOW`, … These are data
  (ImpEx), not items.xml.
- One standard `PriceRow` per group × currency, with `pg` = that group and **no `product`**. All
  standard dimensions still apply: `net`, `unit`, `startTime`/`endTime`, user price group `ug`,
  `minqtd`. Validity dates and customer-group prices work out of the box.
- Example, EUR, gross: `SVC_INSTALLATION` LOW 90.00 / MEDIUM 120.00 / HIGH 150.00;
  `SVC_WARRANTY_3Y` LOW 39.00 / MEDIUM 79.00 / HIGH 129.00.
- **Service products have no product-specific price rows.** The platform ranks a product-specific
  row above a group row, so one would override every condition price. Sample data must not create
  any, and a test asserts the lookup still returns the group price.
- **No group is assigned to the service product itself** (`SVC_INSTALLATION.Europe1PriceFactory_PPG`
  stays empty). The group is supplied per cart line by the hook (§5.2), because the platform takes
  the price group from the entry's product (`PDTEnumGroupsHelper.getPPG(product)`), with no
  entry-level override for price groups.
- Group rows aren't tied to a product, so they're maintained in Backoffice's price row list,
  filtered by product price group, not on the service product's Prices tab. The implementer
  verifies whether these rows carry a `catalogVersion` in this installation's price setup
  (`CatalogAwareEurope1PriceFactory`) and how they reach Online. Mirror how existing electronics
  price rows are imported, and record it in the PR.
- Volume stays tiny: services × conditions × currencies.
- The `<serviceCode>_<conditionCode>` convention is the only coupling between conditions and price
  groups. A missing group or row means "no price", which the stale-service handling (§5.4) covers.

### 4.5 Entry groups

Add the value `SERVICE` to the dynamic `GroupType` enum (§5.3).

## 5. Design

### 5.1 Lookup service (`customservices`)

`ProductServiceLookupService` (new):

- `getAvailableServices(ProductModel product)`: the active `SERVICE` references' targets, in
  reference order. Returns an empty list if the product has no `servicePriceCondition`. Runs under
  the session's catalog versions, so the storefront sees Online only.
- `getServicePriceGroup(ServiceProductModel service, ServicePriceCondition condition)` resolves the
  `ProductPriceGroup` with code `<serviceCode>_<conditionCode>` through `EnumerationService`, or
  returns empty.
- `getServicePrice(ServiceProductModel service, ProductModel product)` returns
  `Optional<PriceInformation>` for display. It's the **single source** of the shown service price for
  the current session currency, user and date. It builds standard price criteria
  (`DefaultPriceValueInfoCriteria.buildForInfo()`), the same way
  `DefaultPDTCriteriaFactory.priceInfoCriteriaFromBaseCriteria` does, but with
  `withProductPriceGroup(<resolved group>)`, and calls the platform's `findPriceValueInfoStrategy`.
  No custom matching.

The cart page (§5.7) and the price hook (§5.2) both call `getServicePrice`, so the price shown
before ticking always equals the price charged.

### 5.2 Pricing: `ServiceFindPriceHook implements FindPriceHook`

Added to the platform's `findPriceHooks` list with a `listMergeDirective` in
`customservices-spring.xml`, following `subscriptionservices-spring.xml`
(`subscriptionPriceFindPriceHookMergeDirective`).

- `isApplicable(entry)`: true only if `entry.product` is a `ServiceProduct`.
- `findCustomBasePrice(entry, defaultPrice)`:
  1. Resolve the linked product entry through the entry's `SERVICE` group (§5.3).
  2. Read that product's `servicePriceCondition` and resolve the price group (§5.1).
  3. Build the criteria exactly as `DefaultPDTCriteriaFactory.priceValueCriteriaFromOrderEntry(entry)`
     does: the entry's product, user, user price group, quantity, unit, and the order's currency,
     date and net. Then **replace only the product price group** with the resolved group.
  4. Call the platform's `findPriceValueInfoStrategy.getPDTValues(criteria)` and return the first
     value.
  5. If any step yields nothing (no linked entry, no condition, no group, no matching row), fail the
     calculation (§5.4 covers the cleanup). **Never** return `defaultPrice` for a service entry.

### 5.2a Hooks before the default lookup: `ServiceAwareSLFindPriceStrategy`

**Verified on the live server during implementation (corrects an earlier wrong claim in this
spec):** `DefaultSLFindPriceStrategy.findBasePrice` (`platformservices`, line ~39) runs the default
lookup *first*. That lookup ends in `DefaultFindPriceValueInfoStrategy.getPDTValues` (line ~47),
which **throws** `CalculationException: No price defined for product SVC_INSTALLATION, pg: null`
when no row matches. The `null` branch at line ~52 is never reached, so the hook never runs.

Fix: replace the platform's `slFindPriceStrategy` alias with a small subclass,
`ServiceAwareSLFindPriceStrategy extends DefaultSLFindPriceStrategy` (`customservices`), declared
with `parent="defaultSLFindPriceStrategy"` and taking over the alias in `customservices-spring.xml`.
Standard hybris override style, about 15 lines:

- `findBasePrice(entry)`: if any hook in `findPriceHooks` is applicable to the entry, call the first
  applicable one with `defaultPrice = null` and return its result, **without** running the default
  lookup. Otherwise, call `super.findBasePrice(entry)` unchanged.
- No other method is overridden. Every non-service entry goes through the platform code exactly as
  before.
- This is the **only replaced platform price class** in this feature. Note it in the PR as an
  upgrade-check item: on every SAP upgrade, re-check `DefaultSLFindPriceStrategy.findBasePrice`
  against the subclass.
- Hooks from other modules (subscriptions, product configurator) aren't active in this installation.
  If one ever becomes applicable, it would now also run without a default price, so the subclass
  must only skip the default lookup when the applicable hook is `ServiceFindPriceHook`. For any other
  applicable hook, keep the platform's original order (default lookup first, then hook).

Rejected alternatives, recorded so they aren't revisited:

- A dummy fallback price row on each service product, so the default lookup succeeds. If the hook
  ever weren't applied, a fake price would be charged.
- A processor in front of the default lookup. Its criteria carry no cart entry, so it would need a
  thread-local to find the linked product.

**Discounts, promotions, taxes:** the service entry is a normal cart entry for the service product
with a correct base price. Europe1 discount rows on the service product or its discount group,
promotion-engine rules targeting the service product or a services category, taxes from the service
product's tax group, and cart and order totals all work out of the box. No changes there, and none
to the dishwasher's own price.

The implementer must verify:

1. ~~That `DefaultSLFindPriceStrategy` is the active price strategy~~: **verified** on the live
   server (`FindPDTValuesInformationsSwitcher` → `DefaultSLFindPriceStrategy`). Still required: an
   integration test that calculates a cart containing a service entry through the real calculation
   service with `ServiceAwareSLFindPriceStrategy` in place. It must return the condition's price (for
   example MEDIUM 120.00) and leave the product entry's price unchanged.
2. That the `slFindPriceStrategy` alias really resolves to the subclass at runtime, and that both
   beans wired with `findPriceHooks` in `order-spring.xml` (~250 and ~273) end up on the path that
   prices cart entries.
3. ~~Group-only rows leaking into normal products~~: **verified**. A normal product's price is
   unaffected (86.86 × 2 = 173.72 with the service rows present).

### 5.3 Linking a service entry to its product entry (entry groups)

Each product entry that has at least one service gets an `EntryGroup` of type `SERVICE` on the
cart. The product entry and all of its service entries carry that group's number in
`entryGroupNumbers`.

- This is the platform's own mechanism for related entries, so it survives entry renumbering and
  the cart→order clone. A direct reference between entries would risk pointing an order entry back
  at a cart entry after the clone.
- Within a `SERVICE` group, the single entry whose product is not a `ServiceProduct` is the product
  entry.
- **Cart→order clone: verified.** `entryGroups` and `entryGroupNumbers` are preserved.
- **Add-to-cart merge: verified as a problem, fix decided.** The platform's
  `EntryMergeFilterEntryGroup` refuses to merge an existing product line in group {1} with a new add
  that has no groups. Adding a second DW-500 would create a duplicate product line. Fix: override
  that merge filter bean (`customservices`) so **`SERVICE`-type groups are ignored** when comparing.
  Groups of any other type keep the platform behaviour. Service entries themselves are never merged
  through add-to-cart. They are only created and changed by `CartServiceSelectionService` (§5.4).

### 5.4 Cart operations (`customservices`)

`CartServiceSelectionService` (new), using the commerce cart strategies rather than raw model
manipulation, so that recalculation and cart hooks run:

- `addService(cart, productEntryNumber, serviceCode)`: validates that the entry is a product entry,
  the service is in `getAvailableServices(product)`, and a price exists. Otherwise it fails with no
  change. If the service is already attached to that entry, it does nothing. Otherwise it creates the
  service entry with **quantity = the product entry's quantity**, linked through the entry group.
- `removeService(cart, productEntryNumber, serviceCode)`: removes that service entry only.
- **Quantity sync**: when a product entry's quantity changes, its service entries are set to the
  same quantity in the same operation. Hook this into the commerce update-cart-entry path, not just
  the controller, so every path is covered: cart page update, merge on login, restored carts.
- **Cascade**: removing a product entry, or setting its quantity to 0, removes its service entries
  and its `SERVICE` group.
- **Direct quantity edits** on a service entry through the existing update endpoint are rejected.
- **Stale services**: when the cart page loads, a service entry that's no longer valid is removed
  and the shopper gets an English info message. Invalid means the reference was deactivated or
  removed, the product's condition was removed, or no matching price row exists. The cart must
  always load. Nothing is charged at a stale or zero price.
- **Cart merge on login**: product and service lines from an anonymous cart keep their linkage and
  quantities after merging. If the same product with the same service is in both carts, the merged
  line ends up with one service entry at the merged quantity. Approach: merge the plain product
  lines through the platform (with `SERVICE` groups ignored, §5.3), then re-attach the anonymous
  cart's services through `addService`. Quantity sync then sets their quantity.

### 5.5 Stock

Services aren't physical. They must never be blocked by stock checks or reduce stock. With the
stock system enabled, `AbstractCommerceCartStrategy.getAvailableStockLevel` asks
`commerceStockService` for a level. A null result means "force in stock", but a product with **no**
`StockLevel` rows gets whatever `commerceStockLevelCalculationStrategy` computes for an empty list.

**Verified:** a service product with no `StockLevel` rows gets stock **0** (a normal product got
99), so it would be blocked. The electronics store has **20 warehouses**.

**Decided: code, not data.** A `commerceStockService` override (`customservices`, subclass of the
platform's default, taking over its alias) returns "force in stock" (`null` level /
`StockLevelStatus.INSTOCK`) for any `ServiceProduct` and delegates for everything else. No
`StockLevel` rows exist for services. Per-warehouse `FORCEINSTOCK` rows (the rejected data option)
would need maintaining for every new warehouse, and a forgotten row would silently block services.
Services never reserve or reduce stock.

### 5.6 Order and fulfilment

- Placing an order with service entries must not break order placement or the existing consignment
  split (`customfulfilmentprocessOrderSplittingService`: `splitByAvailableCount`, `splitByWarehouse`,
  …). The implementer places a real order with a product plus services and inspects the
  consignments.
- A service entry must not produce a shipment with no physical item, or a consignment that can never
  ship and blocks the order process. If the existing split strategies can't keep it that way
  unchanged, **stop and report** before modifying fulfilment.
- Delivery cost is unaffected by services.

### 5.7 Cart page (`customstorefront`) and facades (`customfacades`)

- Below each product line with at least one available, priced service, add a "Services" block. It
  has one checkbox per service: service name and its price for this product. When quantity > 1,
  also show the line total, e.g. "Installation, 120.00 each (240.00)". The checkbox is checked if
  the service is attached.
- Checking or unchecking sends `POST /cart/entry/{entryNumber}/services/{serviceCode}/add` or
  `.../remove`, CSRF-protected like every other cart mutation, then redirects to `/cart`. AJAX
  without a reload is optional.
- **Service entries are not separate cart lines** in the UI. They're shown only inside their
  product's block, with no own quantity selector or remove link. Line counts and the minicart item
  count reflect product lines only. Totals include services.
- Lines without available services look exactly as today.
- Checkout order summary, order confirmation page, order history detail and confirmation email show
  selected services under their product with the charged price, read-only.
- DTOs: extend cart and order entry data with available and selected services, declared in
  `customfacades-beans.xml` and generated. Don't hand-write them; that was called out in the NET-8941
  PR #1 review. Fill them with a populator added through `modifyPopulatorList`, not by replacing
  the converter. Service entries are excluded from the top-level entry list the cart page renders.
- New English message keys go in `base_en.properties` only.

### 5.8 Backoffice (`custombackoffice`)

- `ServiceProduct` is creatable and editable.
- Service prices are maintained as standard price rows filtered by product price group. This needs
  no custom configuration if Backoffice's existing price row search already allows it. Verify and
  note it in the PR.
- `Product.servicePriceCondition` is editable on the product editor.
- `SERVICE` references are maintained on the product's existing references section.
- Keep configuration minimal, with no new widgets.

### 5.9 ImpEx and sample data

- Essential (`customcore`, from `CoreSystemSetup`, ESSENTIAL): anything type-level not created by
  items.xml alone, such as enum values if needed.
- Sample (from `CustomservicesSystemSetup`, PROJECT): the electronics product catalog, Staged, then
  Online through the catalog sync. **`custominitialdata` is not in `config/localextensions.xml` and
  has no classes, so its `InitialDataSystemSetup` never runs** (verified). The sample data therefore
  lives in `customservices`, next to the existing cronjob ImpEx registered there. It can move if
  `custominitialdata` is activated later. The Staged→Online sync was verified to carry
  `ServiceProduct`, `servicePriceCondition` and the `SERVICE` references without sync-job changes.
  - `SVC_INSTALLATION` and `SVC_WARRANTY_3Y`: English name and description, tax group, stock per
    §5.5, and **no** product price rows.
  - `ProductPriceGroup` values `SVC_INSTALLATION_{LOW,MEDIUM,HIGH}` and `SVC_WARRANTY_3Y_{LOW,MEDIUM,HIGH}`,
    and a group `PriceRow` for each in every currency the electronics store uses.
  - A handful of real electronics products with a `servicePriceCondition` and `SERVICE`
    references, covering at least: one product per condition; one with warranty only; one with a
    condition but no references; one with references but no condition. The last two show no
    services.
- Verify with `ant updatesystem` (pre-approved). Never run `ant initialize`.

## 6. Acceptance criteria

1. A cart line shows exactly the services linked to its product through active `SERVICE` references,
   in reference order. This happens only if the product has a `servicePriceCondition`. Other lines
   look as today.
2. The price shown for a service is the standard `PriceRow` of price group
   `<serviceCode>_<productCondition>`, in the session currency. The same service shows LOW / MEDIUM / HIGH prices on products with those conditions.
3. Ticking a service adds a **separate** cart entry for the shared service product. The product's
   own entry price is unchanged. The cart total increases by service price × line quantity.
   Unticking removes the entry and restores the previous total.
4. Changing the product line's quantity changes its services' quantities to match. Removing the
   product line removes its services.
5. A Europe1 discount or promotion-engine rule targeting a service product (or a services category)
   applies to service entries with no custom code, and doesn't affect the product's entry.
6. Services can't be added through the normal add-to-cart endpoints, aren't searchable, and have no
   reachable product page. They never appear in the PDP's product-reference components.
7. Services are never blocked by stock and don't change stock levels.
8. Placing an order with a product plus services succeeds. The order, confirmation page, order
   history and email show services under their product with the charged price. No service-only
   shipment is created.
9. An anonymous shopper's selected services survive login and cart merge with correct linkage and
   quantities.
10. The price charged for a service always equals the price shown next to its checkbox.
11. When a service becomes invalid while it's in a cart (reference removed, condition removed, price
    row removed), the cart still loads, the service is dropped, and the shopper sees an English
    message.
12. No catalog id, store id, product code, condition or price appears in a `.properties` file.
13. Every `items.xml` change, including the `ProductReferenceTypeEnum` extension, is proven by an
    actual `ant all`. New ImpEx is wired into `SystemSetup` and verified with `ant updatesystem`.
14. Tests cover: the hook (applicable/not applicable; each condition; missing link, condition, group
    or price), the lookup service, add/remove, quantity sync, cascade removal, and a cart calculation
    integration test proving the hook is active. Also: a service product with an accidental
    product-specific row (documents the ranking), and a group-only row not affecting a normal
    product's price.
15. `PriceRow` is neither subtyped nor extended. No new price-related type exists.
16. The only replaced platform price class is `ServiceAwareSLFindPriceStrategy` (§5.2a). Every
    non-service entry is priced through unchanged platform code, proven by a test where a cart
    without services calculates exactly as before.
17. Adding a product that's already in the cart with services attached increases that line's
    quantity, and its services' quantities with it. It doesn't create a second product line.

## 7. Non-goals

- A per-service price condition on a product.
- Services that depend on other services, such as "installation requires warranty".
- More than one instance of the same service per product line.
- Fulfilling the service after the order.
- Service selection outside the cart page.

## 8. Open questions

1. ~~Pricing model~~: **decided**. Standard `PriceRow`s on out-of-the-box product price groups
   (`<serviceCode>_<condition>`). The product's `servicePriceCondition` selects the group, and a
   `FindPriceHook` passes it to the platform's standard price lookup (§4.4, §5.2). No `PriceRow`
   subtype or new attribute.
2. ~~Per-pair prices / variants~~: **ruled out** (§0).
3. ~~Which services apply to which product~~: **decided**. Explicit `ProductReference` of type
   `SERVICE` (§4.3).
4. ~~One condition per product vs. per service~~: **decided**. One per product, for now.
5. ~~Stock handling for services~~: **decided**. A `commerceStockService` override, because of 20
   warehouses (§5.5).
6. **Consignment handling** (§5.6): verify with a real order; stop and report if fulfilment changes
   are needed.
7. ~~Default price lookup on a service product~~: **resolved**. It throws, not returns `null` (the
   earlier claim here was wrong), so hooks never ran. **Decided: option A** with
   `ServiceAwareSLFindPriceStrategy` (§5.2a). The variant-per-condition alternative (option B) was
   rejected by the user (§0).
8. ~~Add-to-cart merge with entry groups~~: **decided**. The `EntryMergeFilterEntryGroup` override
   ignores `SERVICE` groups (§5.3).
9. ~~Where sample data runs~~: **decided**. `CustomservicesSystemSetup`, because `custominitialdata`
    is inactive (§5.9).
10. **Return or cancel of a service independently of its product**: not addressed. Order management
   behaviour stays as today. Raise it with the user if it comes up in testing.

## 9. Implementation notes (as built)

Recorded after implementation and review, so the spec matches the code. None of these change what the shopper sees.

1. **Class and bean names that differ from the text above.** The project-data class is `ProductServicesSystemSetup`
   (own bean `productServicesSystemSetup`), not `CustomservicesSystemSetup`. A `@SystemSetup` class without a Spring bean is
   never instantiated: `CustomservicesSystemSetup` has none, so its NET-8939 / NET-8940 ImpEx has never been imported by a
   system update. That is a separate, pre-existing defect and was left alone.
2. **ImpEx location.** Classpath ImpEx must sit under `<ext>/resources/<ext>/...`. The sample data is
   `customservices/resources/customservices/impex/customservices-productservices-sampledata.impex`.
3. **Service products are hidden by a search restriction, not an add-to-cart validator.** `Frontend_ServiceProduct`
   (`customergroup`, essential data in `customcore`) empties every storefront product lookup: no product page, quick view or
   add-by-code. A validator was tried and removed because restoring a saved cart re-adds every entry through the add-to-cart
   strategy and the validator broke it. `getAvailableServices` reads the product's references with search restrictions
   disabled, since reading a relation also applies them. Only `APPROVED` services are offered.
4. **Stale services are removed in more places than §5.4 lists.** The `beforeCalculate` hook removes an invalid service
   entry before any calculation (restore, update, add, merge, checkout), because pricing it would throw. The names are kept
   in a session attribute and the cart page shows the "no longer available" message once. Placing an order with a stale
   service is refused (`InvalidCartException`) instead of dropping a line silently.
5. **Quantity sync** also runs from a calculation hook and an add-to-cart hook, not only the update hook: merging a plain
   add into an existing line goes through `CartService.updateQuantities`, which calls no update hook.
6. **`addService` uses `CartService.addNewEntry`** (not the add-to-cart strategy), in the service's own unit, and undoes
   itself if the recalculation fails. No add-to-cart hooks or max-order-quantity rules apply to a service.
7. **Solr exclusion** is a rewrite of the `SolrIndexerQuery` text (adds `NOT IN ServiceProduct`), applied as essential data on
   every update and again after sample data. It is not durable against a later re-import of a store's Solr ImpEx, and a
   fresh `ant initialize` relies on the project step running after the store's project data. A durable version would put the
   clause into each store's indexer query data.
8. **Service prices are global.** Group rows have no product and no `catalogVersion` (the installation's other price rows
   carry none either), and the group code is `<serviceCode>_<condition>`, so all stores sharing a currency share service
   prices. Store-specific service prices need different service codes or user price groups.
9. **Order DTOs.** Service entries are removed from `entries`, `unconsignedEntries`, consignment entries, delivery/pickup
   groups; `SERVICE` root groups render as standalone lines. When a product line is split across several consignments each
   consignment entry carries the whole line's services, and a consignment holding only services would be empty. Fulfilment
   is not active in this installation, so that is unverified (§5.6 stays open).
10. **Not in this installation:** the confirmation email lists no order lines at all (nothing to extend); Backoffice does not
    start with the current database (a leftover `SavedForLaterEntry` type from another ticket), so the editor tab is
    unverified; `custominitialdata`, `customfulfilmentprocess` and `order-process` are not active.
