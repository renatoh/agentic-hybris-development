# NET-8938 — Order history in My Account from a REST endpoint

| | |
|---|---|
| **Ticket** | NET-8938 |
| **Status** | Phase 1 (order history list) implemented and verified. Phase 2 (order detail, §5.5 / §6.2) specified, not implemented. |
| **Extension** | `customservices` (new code), `yacceleratorstorefront` (wiring only) |
| **Platform** | SAP Commerce Cloud 2211.37 |

## 1. Context

The My Account order history page (`/my-account/orders`,
`AccountPageController.orders()` in
`bin/modules/base-accelerator/deprecated/yacceleratorstorefront/web/src/de/hybris/platform/yacceleratorstorefront/controllers/pages/AccountPageController.java`)
currently reads orders from the Commerce DB via the platform `orderFacade`
(`orderFacade.getPagedOrderHistoryForStatuses(pageableData)`), which resolves down to
`OrderService` / `OrderDao` and the `Orders` table.

Orders are in fact owned by an external system. The Commerce DB copy is not authoritative
and must no longer be shown in My Account.

## 2. Goal

Replace the source of the My Account order history with a call to an external REST endpoint,
implemented as our own Spring service inside the `customservices` extension.

- The order history read from the hybris DB is **ignored** — it is not merged with, nor used
  as a fallback for, the REST result.
- The endpoint URL is **not** hardcoded: it comes from the property file.
- The real endpoint **does not exist yet**. For now a **mock implementation** is delivered that
  returns the contents of `customservices/resources/mockorderhistory/order_history_mock.json`.
  Switching from mock to real must not require code changes in the storefront.

## 3. Scope

**In scope**

- New Spring service in `customservices` that supplies the order history of the current customer.
- A mock implementation reading the bundled JSON file.
- A REST implementation skeleton reading the endpoint URL from configuration.
- Wiring into the My Account order history page so the list shown to the user comes from the service.
- **The order detail page** (`/my-account/order/{orderCode}`) — moved in scope, see §5.5 and §6.2.
  It currently still falls through to `DefaultOrderFacade.getOrderDetailsForCode(...)` and therefore
  reads the DB, which contradicts §2. Clicking a row in the list must not reach the DB.

**Out of scope (unless decided otherwise — see §8)**

- The guest / anonymous order detail variants `getOrderDetailsForCodeWithoutUser(String)` and
  `getOrderDetailsForGUID(String)` (§8 q7). They remain on the DB for now.
- OCC / Commerce Web Services order endpoints.
- Order placement, reorder, cancellation, and any write path.
- Removing or archiving `Order` data in the DB.

## 4. Source data contract (mock)

`customservices/resources/mockorderhistory/order_history_mock.json` — one customer, 10 orders:

```json
{
  "customerNumber": "C-100234",
  "orders": [
    {
      "orderNumber": "SO-100001",
      "orderDate": "2026-01-15",
      "status": "COMPLETED",
      "currency": "EUR",
      "totalPrice": 169.56,
      "positions": [
        { "positionNumber": 1, "productReference": "SKU-10001", "quantity": 3, "price": 56.52 }
      ]
    }
  ]
}
```

| Field | Type | Notes |
|---|---|---|
| `customerNumber` | string | External customer id |
| `orders[].orderNumber` | string | External order number, shown as the order code |
| `orders[].orderDate` | string | ISO date `yyyy-MM-dd`, no time, no timezone |
| `orders[].status` | string | External status, see §6.1 for the allowed values and their mapping |
| `orders[].currency` | string | ISO 4217 code, e.g. `EUR` — used for formatting, the session currency is **not** used |
| `orders[].totalPrice` | number | Order gross total as delivered by the external system |
| `orders[].positions[].positionNumber` | int | 1-based |
| `orders[].positions[].productReference` | string | External SKU — not guaranteed to exist in the Commerce catalog |
| `orders[].positions[].quantity` | int | |
| `orders[].positions[].price` | number | Line total for that position, in `currency` |

`totalPrice` is authoritative and is **displayed as delivered** — it is not recalculated from the
positions. The mock values happen to equal `sum(quantity * price)`, but the real endpoint may include
shipping, discounts, or taxes that the positions do not carry.

**The detail view uses this same payload — no second file and no new fields are introduced.** The
single-order lookup iterates `orders[]` and matches on `orderNumber`; the `positions[]` already
present on each order are the detail line items. The payload carries **no** delivery address, payment
information, delivery mode, subtotal, tax or shipping cost — §6.2 defines what happens to those.

## 5. Design

### 5.1 Service layer (`customservices`)

Package `com.custom.orderhistory` (sub-packages `service`, `dto`, `populator`).

```
CustomOrderHistoryService              (interface)
 ├─ getOrderHistory(String customerNumber) : CustomOrderHistoryResponse
 └─ getOrderDetail(String customerNumber, String orderNumber) : Optional<CustomOrderDto>

MockCustomOrderHistoryService          reads the bundled JSON from the classpath
RestCustomOrderHistoryService          calls the configured REST endpoint
```

`getOrderDetail` returns `Optional.empty()` when no order in the payload has that `orderNumber` —
it does **not** throw for a simple miss, and it does **not** return a partially populated object.
Only a transport/parse failure throws `CustomOrderHistoryException`. This keeps "order not found"
(a normal 404-ish outcome, handled in §5.5) distinct from "the backend is broken".

- **Mock implementation**: calls `getOrderHistory(...)`, iterates `orders[]`, returns the first whose
  `orderNumber` equals the requested code (exact match, case-sensitive). No caching, no index — the
  payload is 10 orders.
- **REST implementation**: `GET {customservices.orderhistory.rest.endpoint.url}/{orderNumber}`, same
  `RestTemplate` and timeouts as the list call. A `404` maps to `Optional.empty()`; any other
  non-2xx or a transport error throws `CustomOrderHistoryException`.

- DTOs (`CustomOrderHistoryResponse`, `CustomOrderDto`, `CustomOrderPositionDto`) are plain POJOs
  in `src/`, deserialized with Jackson. They are *not* hybris `-beans.xml` types and *not* item types —
  nothing from this ticket is persisted.
- The mock reads the file from the classpath (`/mockorderhistory/order_history_mock.json`), not from
  an absolute filesystem path, so it works in a packaged deployment.
- The REST implementation uses a `RestTemplate` (bean declared in `customservices-spring.xml`) with
  explicit connect/read timeouts. On a failure it must throw a dedicated
  `CustomOrderHistoryException` rather than silently returning an empty list.

### 5.2 Bean wiring

In `customservices-spring.xml`:

```xml
<alias name="mockCustomOrderHistoryService" alias="customOrderHistoryService"/>
```

Switching to the real endpoint = changing that alias to `restCustomOrderHistoryService`.
No storefront change, no Java change.

### 5.3 Storefront integration — `CustomOrderFacade`

`CustomOrderFacade` **extends the platform `DefaultOrderFacade`** and overrides **only** the order
history methods, so that every other facade method (order details, checkout-related calls) keeps the
unchanged platform behaviour:

```java
package com.custom.orderhistory.facade;

public class CustomOrderFacade extends DefaultOrderFacade
{
    private CustomOrderHistoryService customOrderHistoryService;
    private Converter<CustomOrderDto, OrderHistoryData> customOrderHistoryConverter;
    private Converter<CustomOrderDto, OrderData> customOrderDetailConverter;

    // list — phase 1, implemented
    public SearchPageData<OrderHistoryData> getPagedOrderHistory(PageableData pageableData) { ... }

    @Override
    public SearchPageData<OrderHistoryData> getPagedOrderHistoryForStatuses(
            PageableData pageableData, OrderStatus... statuses) { ... }

    // detail — phase 2, §5.5
    @Override
    public OrderData getOrderDetailsForCode(String code) { ... }
}
```

> **Verified against the platform, 2026-09-13.** `getPagedOrderHistory(PageableData)` is **not**
> declared on `OrderFacade` or `DefaultOrderFacade` — an earlier draft of this spec was wrong about
> that. It is kept as a plain delegating method **without** `@Override`. The interface declares
> `getPagedOrderHistoryForStatuses(PageableData, OrderStatus...)`,
> `getOrderHistoryForStatuses(OrderStatus...)`, `getOrderDetailsForCode(String)`,
> `getOrderDetailsForGUID(String)` and `getOrderDetailsForCodeWithoutUser(String)`.

- Both overrides call `customOrderHistoryService`, convert to `OrderHistoryData`, sort by
  `orderDate` descending and apply paging in memory over the full response, filling
  `SearchPageData.pagination` (`totalNumberOfResults`, `numberOfPages`, `currentPage`, `pageSize`)
  so the existing paging tags in `orders.jsp` keep working.
- `getPagedOrderHistoryForStatuses` filters on the **mapped** hybris `OrderStatus` (§6.1). When the
  caller passes no statuses, no filter is applied.
- `super.` is **not** called in the overrides — the DB history is ignored, not merged.
- Besides the three methods listed above, nothing is overridden. The guest/GUID detail variants
  (§3, §8 q7) stay on the DB.

Bean wiring in `customservices-spring.xml` replaces the platform facade under its existing name, so
`AccountPageController` needs no change:

```xml
<bean id="customOrderFacade" class="com.custom.orderhistory.facade.CustomOrderFacade"
      parent="defaultOrderFacade">
    <property name="customOrderHistoryService" ref="customOrderHistoryService"/>
    <property name="customOrderHistoryConverter" ref="customOrderHistoryConverter"/>
</bean>

<alias name="customOrderFacade" alias="orderFacade"/>
```

`parent="defaultOrderFacade"` inherits the platform dependency injection, so no platform collaborator
has to be re-declared by hand.

This is a **B2C-only** scope: no B2B order history variant (approval workflows, `B2BOrderFacade`,
organisation-wide order lists) is considered.

### 5.4 Configuration

Defaults in `customservices/project.properties`, environment values in `config/local.properties`:

| Property | Default | Purpose |
|---|---|---|
| `customservices.orderhistory.rest.endpoint.url` | *(empty)* | Base URL of the external order history endpoint |
| `customservices.orderhistory.rest.connect.timeout.ms` | `5000` | Connect timeout |
| `customservices.orderhistory.rest.read.timeout.ms` | `10000` | Read timeout |
| `customservices.orderhistory.mock.resource` | `/mockorderhistory/order_history_mock.json` | Classpath location used by the mock |

Read via `ConfigurationService`, resolved **per call** (not cached in a field), so the value can be
changed in HAC without a restart.

### 5.5 Order detail integration

`AccountPageController.order(...)` is mapped to `/my-account/order/{orderCode}`, is annotated
`@RequireHardLogIn`, and calls `orderFacade.getOrderDetailsForCode(orderCode)`. Because the
`orderFacade` alias already points at `customOrderFacade` (§5.3), overriding that one method is
sufficient — **the controller and the JSPs are not touched.**

```java
@Override
public OrderData getOrderDetailsForCode(final String code)
{
    return customOrderHistoryService
            .getOrderDetail(resolveCustomerNumber(), code)
            .map(customOrderDetailConverter::convert)
            .orElseThrow(() -> new UnknownIdentifierException("Order " + code + " not found"));
}
```

- `super.getOrderDetailsForCode(...)` is **never** called. There is no DB fallback — that is the
  whole point of this change.
- **Unknown order code throws `UnknownIdentifierException`.** The controller already catches exactly
  that and redirects to the order history page with the `system.error.page.not.found` flash message,
  so the not-found path needs no new code and no new JSP. Do not invent a different exception.
- The converter is registered as a bean in `customservices-spring.xml` alongside the existing
  `customOrderHistoryConverter`.

**Authorisation note.** The platform implementation scopes the lookup to the current user, so one
customer cannot read another's order by guessing a code. The mock returns the single bundled payload
regardless of who is logged in, which means that protection is *not* reproduced by the mock. This is
acceptable for the mock only. The REST implementation **must** pass the resolved customer number to
the endpoint and must not accept an order that comes back with a different `customerNumber` — see
§8 q1, which this makes blocking for the real endpoint.

## 6. Mapping to `OrderHistoryData`

| `OrderHistoryData` | Source | Note |
|---|---|---|
| `code` | `orderNumber` | |
| `placed` | `orderDate` parsed as `yyyy-MM-dd` | Time component is `00:00` in the platform timezone |
| `total` | `totalPrice` + `currency` | Formatted as `PriceData` via `PriceDataFactory`, taken as delivered |
| `status` | mapped from `status`, see §6.1 | |
| `statusDisplay` | mapped from `status`, see §6.1 | Localized via the existing `text.account.order.status.display.*` keys |
| `guid` | — | Not applicable, left null |

### 6.1 Status mapping

| External `status` | hybris `OrderStatus` | `statusDisplay` |
|---|---|---|
| `OPEN` | `CREATED` | `created` |
| `IN_PROCESS` | `PROCESSING` | `processing` |
| `SHIPPED` | `COMPLETED` | `shipped` |
| `COMPLETED` | `COMPLETED` | `completed` |
| `CANCELLED` | `CANCELLED` | `cancelled` |

An unknown or missing external status maps to `status = null` and `statusDisplay = "unknown"`; it is
logged at WARN level and the order is still shown. The mapping lives in one place
(`CustomOrderStatusMapper`) so it can be extended without touching the converter.

Currency comes from the payload per order, so a customer with orders in more than one currency is
rendered correctly.

Customer number: the mock payload is keyed by `customerNumber` (`C-100234`). Which Commerce
customer attribute maps to it is open (§8); for the mock the file is returned regardless of the
logged-in user.

### 6.2 Mapping to `OrderData` (detail view)

| `OrderData` | Source | Note |
|---|---|---|
| `code` | `orderNumber` | |
| `created` | `orderDate` parsed as `yyyy-MM-dd` | Same parsing as §6, `00:00` platform timezone |
| `status` / `statusDisplay` | mapped from `status` | **Reuses `CustomOrderStatusMapper` from §6.1** — the mapping is not duplicated |
| `totalPrice` | `totalPrice` + `currency` | `PriceData` via `PriceDataFactory`, taken as delivered, never recalculated |
| `unconsignedEntries` | `positions[]` → `List<OrderEntryData>` | See below — this is what the items JSP renders |
| `entries` | same list as `unconsignedEntries` | Populated too, for any tag that reads it |
| `consignments` | — | Empty list, **not null**. The payload has no shipment data |
| `guestCustomer` | `false` | The page is `@RequireHardLogIn` |
| `deliveryAddress`, `paymentInfo`, `deliveryMode`, `deliveryStatus`, `subTotal`, `deliveryCost`, `totalTax` | — | **No source in the payload.** Left unset — see the null-safety note below |

Per position → `OrderEntryData`:

| `OrderEntryData` | Source | Note |
|---|---|---|
| `entryNumber` | `positionNumber` | Payload is 1-based; convert to the platform's 0-based convention |
| `quantity` | `quantity` | as `Long` |
| `basePrice` | `price` + order `currency` | `PriceData`, the per-position line total as delivered |
| `totalPrice` | `price` + order `currency` | Same value — the payload carries one figure per position |
| `product` | `productReference` | See below |

**`productReference` is not resolved against the catalog.** §4 states it is not guaranteed to exist
as a Commerce product, and a lookup that throws for a missing SKU would break the whole page for one
bad line. Build a stub `ProductData` with `code` and `name` both set to `productReference`, and no
URL, image, price or stock. A missing product image must render the standard placeholder, not a
broken image. (This resolves §8 q6.)

**Null-safety is the main implementation risk and must be verified by actually opening the page.**
The detail view is assembled from `accountOrderDetailHeadline.jsp`, `accountOrderDetailOverview.jsp`,
`accountOrderDetailShippingInfo.jsp`, `accountOrderDetailItems.jsp`,
`accountOrderDetailOrderTotals.jsp` and `accountOrderDetailActions.jsp`, plus the `order:` tag files.
Those JSPs were written against a DB-backed `OrderData` where delivery address, payment info and the
total breakdown are always present. They live under `bin/modules` and are **off limits**.

So: it is not enough for the page to compile and the server to start — open
`/my-account/order/SO-100001` as a logged-in customer and confirm it renders. If a JSP cannot cope
with an unset field, **do not edit the platform JSP**. Report it, with the JSP and the field, and
propose one of: (a) populating a harmless empty object instead of null, or (b) a storefront-side
override. Choosing between those is the user's call.

## 7. Acceptance criteria

1. A logged-in customer opening `/my-account/orders` sees the 10 orders from
   `order_history_mock.json`, newest first, with the existing paging working.
2. Order number, date, status and total (with the `EUR` symbol) are rendered for every row; the
   totals match `totalPrice` from the payload exactly — no recalculation, no rounding drift.
3. No order from the hybris `Orders` table appears in that list — verified with a customer who has
   DB orders that are *not* in the mock file.
4. `CustomOrderFacade` overrides only `getPagedOrderHistory` and `getPagedOrderHistoryForStatuses`;
   all other order facade behaviour (e.g. the order detail page) is unchanged.
5. `customservices.orderhistory.rest.endpoint.url` exists in `project.properties`, is readable in
   HAC, and is the only place the endpoint URL is defined — no URL literal anywhere in the Java code.
6. Switching the Spring alias from the mock to the REST implementation requires no Java or JSP change.
7. The REST implementation, pointed at an unreachable URL, produces a logged error and an error
   page/empty state — it must not render a stack trace and must not fall back to DB orders.
8. Unit tests in `customservices/testsrc` cover: JSON deserialization of the mock file, the date
   parsing, the status mapping incl. the unknown-status case, and the sorting/paging of the facade.
9. `ant clean all` succeeds; no changes under `bin/platform` or `bin/modules`.

### Detail view (phase 2)

10. Clicking a row on `/my-account/orders` opens `/my-account/order/{orderNumber}` and the page
    renders from the mock payload. Verified in a browser, not only by a green build.
11. The detail page reaches **no** DB order read: `super.getOrderDetailsForCode(...)` is never called
    and no `Orders` table query is issued for that request.
12. Order code, date, status and total on the detail page match the corresponding row in the list
    exactly, and match `order_history_mock.json` — no recalculation, no rounding drift.
13. Every position in the payload appears as a line item with its position number, quantity and
    price; `productReference` is shown as the product identifier even though it does not resolve to
    a catalog product.
14. An unknown order code (e.g. `/my-account/order/SO-999999`) redirects to the order history page
    with the standard not-found message — no stack trace, no white page, no DB lookup.
15. Unit tests cover: the single-order lookup by `orderNumber` including the not-found case, the
    position → `OrderEntryData` mapping, and that the detail converter reuses the §6.1 status mapping.

## 8. Open questions

1. **Customer identification** — which attribute of the Commerce `Customer` is sent as
   `customerNumber` (`uid`, `customerID`, or a custom attribute)?
2. ~~**Order detail page**~~ — **resolved: yes.** Moved in scope, specified in §5.5 / §6.2.
3. **Status vocabulary** — §6.1 is our assumption. Needs confirmation from the owner of the external
   system that `OPEN / IN_PROCESS / SHIPPED / COMPLETED / CANCELLED` is the complete list.
4. **Paging/sorting** — done in memory over the full response for now. Will the real endpoint
   support server-side paging (and should the service interface already take a page/size)?
5. **Caching** — should the REST response be cached per customer for the duration of the session?
6. ~~**Products not in the catalog**~~ — **resolved:** stub `ProductData`, no catalog lookup (§6.2).
7. **Guest / GUID order detail** — `getOrderDetailsForCodeWithoutUser(String)` and
   `getOrderDetailsForGUID(String)` still read the DB. Should the guest order tracking page switch
   too, or is a DB-backed guest view acceptable while My Account is REST-backed?
8. **Missing fields on the detail page** — the payload has no delivery address, payment info or total
   breakdown. Is an order detail page without those sections acceptable to the business, or does the
   external system need to supply them before this ships?

### Resolved

- ~~Order status missing from the payload~~ — added as `orders[].status`, mapping in §6.1.
- ~~Currency missing from the payload~~ — added as `orders[].currency`, per order.
- ~~Order total missing from the payload~~ — added as `orders[].totalPrice`, displayed as delivered.
- ~~Facade approach~~ — `CustomOrderFacade extends DefaultOrderFacade`, only the order history
  methods overridden (§5.3).
- ~~Order detail page scope~~ — in scope, §5.5 / §6.2.
- ~~`getPagedOrderHistory(PageableData)` override~~ — the method does not exist on the platform
  interface; kept as a plain delegate without `@Override` (§5.3).
- ~~Active storefront~~ — `yacceleratorstorefront`, not `yb2bacceleratorstorefront`
  (the latter is commented out in `config/localextensions.xml`).

## 9. Files expected to change

```
bin/custom/customservices/project.properties                    (new properties)
bin/custom/customservices/resources/customservices-spring.xml   (beans + alias)
bin/custom/customservices/src/com/custom/orderhistory/**        (service, dto, facade, mapper)
bin/custom/customservices/testsrc/com/custom/orderhistory/**    (unit tests)
config/local.properties                                         (environment endpoint URL)
```

`bin/custom/customservices/resources/mockorderhistory/order_history_mock.json` already exists and
is used as-is.
