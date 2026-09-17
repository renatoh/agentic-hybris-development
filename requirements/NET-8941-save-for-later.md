# NET-8941 — Save cart items for later

| | |
|---|---|
| **Ticket** | NET-8941 |
| **Status** | Draft — needs agreement before implementation |
| **Extension** | `customservices` (persistence/service) + `customfacades` (facade/DTOs) + `customstorefront` (web layer) |
| **Platform** | SAP Commerce Cloud 2211.37, storefront `customstorefront` (B2C) |

## 0. Original request (verbatim)

> next features, NET-8941: the customer should be able to save cart items for "later", he might
> have filled up the cart but then quickly need to order something else, with that he can save the
> items for later and then add the back into the cart again. it is not a own cart, just items saved
> for later. they will be displayed at the bottom of the cart page - not on the mini cart - so that
> the custoemr can easily add specific items back into the cart. the customer can also remove them
> entirely from the 'saved for later list'
> next to the delte from cart button, there will be another buttom called safe for later, it will
> delete it from the cart but add it to the saved for later list

## 1. Context

A shopper mid-checkout sometimes needs a cart entry out of the way without losing it — e.g. they
want to add something else urgently and don't want the saved item cluttering the cart total. Today
the only options are "leave it in the cart" or "delete it and lose it." This adds a third: move it
to a "saved for later" list, out of the cart, recoverable with one click.

## 2. Goal

On the cart page, each cart entry gets a **"Save for later"** action next to its existing "Remove"
action. Clicking it removes the entry from the cart and adds it to a **"Saved for later"** section
at the bottom of the cart page (never in the minicart). From that section the shopper can **move an
item back into the cart**, or **delete it from the list entirely** (distinct from moving it back).

- **Persisted per customer** — survives logout/login and is visible on another device, unlike
  NET-8940's session-only comparison lists. Requires a **registered, logged-in customer**; see §8.1
  for the anonymous-shopper case.
- **Not a saved cart.** The platform already has a "Saved Carts" feature (`SaveCartFacade`, visible
  via `savedCartCount` in `AbstractCartPageController`) — an entire named, duplicate cart a customer
  can save and resume. This is different and must not be built on top of it or confused with it in
  the UI: it's a flat list of individual saved line items, not a named alternate cart, and the
  customer's *current* cart is untouched apart from the one entry being moved out of it.
- **Design decision (not asked, low-risk enough to just decide — see §5.1 for reasoning): a small,
  dedicated persistent item type**, not a hidden/implicit second `CartModel` per customer. The
  platform does have a line-level `CartEntryActionHandler` framework used internally by Saved Carts
  to move entries between carts, but repurposing that means every saved-for-later customer carries
  an extra always-present `CartModel` that must be kept out of checkout, order history, and the
  "Saved Carts" list — real ongoing risk for a small feature. A dedicated `SavedForLaterEntry` item
  type (customer + product + quantity) is simpler, and moving an item back into the real cart still
  goes through the platform's normal `CartFacade.addToCart(...)` — so stock/price validation is
  still the platform's own code, not reimplemented.

## 3. Scope

**In scope**

- "Save for later" action on each cart entry (cart page only, not minicart).
- Persistent storage of saved items per registered customer.
- "Saved for later" section at the bottom of the cart page: one row per saved item (image, name,
  price, quantity), with "Move to cart" and "Remove" actions per row.
- Moving an item back into the cart via the platform's normal add-to-cart path (merges with an
  existing cart entry of the same product, standard cart behaviour, not reimplemented).
- Removing an item from the saved list entirely (distinct from moving it to the cart) — deletes it,
  does not touch the cart.

**Out of scope (unless decided otherwise — see §8)**

- Anonymous/guest "save for later" (§8.1).
- Any minicart/header indicator for saved items (explicitly cart-page-only per the request).
- Editing the quantity of a saved item directly in the saved-for-later list (quantity is whatever it
  was when saved; changing it happens after moving back to the cart, via the cart's own quantity
  control).
- Any limit on how many items can be saved.
- B2B storefront (commented out; not in scope, consistent with NET-8940).

## 4. Data model

New item type `SavedForLaterEntry` (extends nothing special — a plain persistent item), defined in
`customservices-items.xml`:

| Attribute | Type | Notes |
|---|---|---|
| `customer` | `Customer` | mandatory — via the relation below, not a plain attribute |
| `product` | `Product` | mandatory |
| `quantity` | `java.lang.Long` | mandatory, the quantity that was in the cart when saved |
| `dateSaved` | `java.util.Date` | optional, for ordering the list |

`customer` is attached via a real `<relation>` to `Customer` (`Customer2SavedForLaterEntryRelation`,
giving `CustomerModel.getSavedForLaterEntries()`), not a bare FK attribute — see §5.1a for why, and
the exact relation/index shape.

No `baseStore` attribute — deliberately dropped. `Product` already carries its own
`catalogVersion`; storing a redundant store reference on every entry could drift out of sync if a
product's catalog assignment ever changes, and adds nothing a live lookup can't give — see §5.3.

**Merge on duplicate**: saving a product that's already in that customer's saved list increments
the existing entry's quantity rather than creating a second row for the same product — mirrors how
adding an already-cart-present product to the cart merges quantities, not duplicates lines.

## 5. Design

### 5.1 Why a dedicated item type, not a hidden cart

See §2 — reasoning kept here for the implementer: the platform's Saved Carts feature already proves
the "duplicate cart used as a holding pen" pattern works, but it carries checkout/order/promotion
machinery this feature doesn't need and must actively be kept away from. A small item type has none
of that surface area.

### 5.1a Attaching to Customer

`customer` is a real `<relation>`, not a plain FK attribute — verified against the platform's own
`AbstractOrder2AbstractOrderEntry` relation as the precedent for both the source/target shape and
the explicit index (hybris does **not** auto-index a relation-derived FK; the platform's own
`AbstractOrderEntry` declares an explicit `oeOrd` index for exactly this pattern, so this type needs
the same):

```xml
<relation code="Customer2SavedForLaterEntryRelation" localized="false">
    <sourceElement type="Customer" qualifier="customer" cardinality="one">
        <modifiers read="true" write="true" optional="false"/>
    </sourceElement>
    <targetElement type="SavedForLaterEntry" qualifier="savedForLaterEntries" cardinality="many"
                   collectiontype="list">
        <modifiers read="true" write="false" search="true" optional="true"/>
    </targetElement>
</relation>
```

```xml
<indexes>
    <index name="idx_SavedForLater_Customer">
        <key attribute="customer"/>
    </index>
</indexes>
```

Two reasons for the relation over a bare attribute: (1) Backoffice/admin discoverability —
`CustomerModel.getSavedForLaterEntries()` shows up as a normal related-items tab on the customer,
a plain FK attribute would leave these rows undiscoverable without a dedicated cockpit view; (2)
customer data lifecycle — this is persisted, customer-identifying data, so account
deletion/GDPR "right to be forgotten" flows need to find and remove it. **Open item for the
implementer to verify, not assumed either way**: does deleting a `Customer` cascade-clean its
`SavedForLaterEntry` rows automatically via the relation, or does this need an explicit
`RemoveInterceptor`? Check against this platform version's actual behaviour before assuming.

The `SavedForLaterEntry` deployment/typecode (see the item type definition) is a placeholder and
must be checked against every typecode already in use across `bin/platform`/`bin/modules`/
`bin/custom` before being finalised — a collision is a real schema conflict, not a style nit.

### 5.2 Service layer

`SavedForLaterService` (new, `customservices`):

- `saveForLater(CustomerModel, ProductModel, long quantity)` — creates or merge-increments the
  persistent entry, via `ModelService`.
- `getSavedItems(CustomerModel, BaseStoreModel)` — all saved entries for that customer, filtered to
  products whose `catalogVersion` belongs to the given store's catalogs (derived at query time, not
  a stored field — see §5.3). The `BaseStoreModel` parameter is a filter, not part of the entry's
  identity.
- `moveToCart(CustomerModel, SavedForLaterEntryModel)` — calls `CartFacade`/`CartService`'s normal
  add-to-cart path with the saved entry's product and quantity, then deletes the persistent entry.
  Standard add-to-cart stock/price handling applies unchanged; if the add fails (out of stock, no
  longer purchasable), the entry stays in the saved list and the failure is reported the same way a
  normal add-to-cart failure is.
- `removeSavedItem(CustomerModel, SavedForLaterEntryModel)` — deletes the entry, no cart interaction.

### 5.3 Multi-store scoping

Per the standing multi-basestore design requirement (`sap-commerce-cloud` skill): a saved item must
never be shown against a store it doesn't belong to once a second `BaseStore` exists — but this is
enforced by **filtering at read time**, not by storing a redundant `baseStore` field on the entry
(dropped from §4 after review — a product's own `catalogVersion` already carries this information,
and duplicating it risks drifting out of sync if a product's catalog assignment ever changes).
`getSavedItems(CustomerModel, BaseStoreModel)` filters to entries whose product's `catalogVersion`
belongs to the given `BaseStoreModel`'s catalogs, resolved from the request's own session context —
never a hardcoded store.

### 5.4 Web layer

In `customstorefront`, alongside the existing `CartPageController`/`cartPage.jsp`:

- "Save for later" button rendered next to each cart entry's existing remove action, **visible to
  every shopper including anonymous ones** — a new `POST` endpoint (e.g.
  `/cart/entry/{entryNumber}/save-for-later`), removes the cart entry via the existing
  cart-entry-removal path and calls `saveForLater(...)`.
- Saved-for-later section at the bottom of `cartPage.jsp`, populated via a new facade method
  exposed alongside `CartData` in the model.
- "Move to cart" (`POST /saved-for-later/{id}/move-to-cart`) and "Remove"
  (`POST /saved-for-later/{id}/remove`) actions per saved row, both redirecting back to `/cart`.

**Anonymous flow (§8.1, decided — stays `POST`, no GET fallback)**: the button is always visible —
clicking it as an anonymous shopper forces a login, and the save completes automatically once
authenticated, landing back on `/cart` with the item already in the saved-for-later list, not just
back on the cart page requiring a second click. `POST` was chosen over a GET-triggered variant
specifically to keep this endpoint CSRF-protected like every other cart mutation in this storefront
— that trade-off costs the small amount of custom pending-action code below, which is worth it for
the consistency.

Two things ruled out during design, worth recording so they aren't tried again:

- **Not** `@RequireHardLogIn`. This storefront already has that annotation/interceptor
  (`RequireHardLoginBeforeControllerHandler`, already used on `CartPageController`) for "this action
  needs a login," but it's a *before-controller* interceptor that blocks the controller method from
  running at all when redirecting — so it never gets a chance to see or store *which* product was
  being saved. Verified by reading its actual implementation, not assumed.
- **Not** Spring Security's `RequestCache`/saved-request replay either — verified that its default
  post-login redirect is a plain `sendRedirect` (browser re-issues as GET), so a `POST`-only
  endpoint is simply never reached by that automatic replay; relying on it would have meant either
  switching this endpoint to GET (the CSRF trade-off already ruled out) or writing a custom
  `AuthenticationSuccessHandler` — more Spring Security surface area than the plain approach below.

Actual design — a small amount of custom, application-level code, not a security-layer
customization:

1. `POST /cart/entry/{entryNumber}/save-for-later`: if `userFacade.isAnonymousUser()`, store the
   product code in a session attribute (quantity doesn't need storing — it's re-read from the
   still-present cart entry once logged in) and redirect to `/login`; do **not** remove the entry
   from the cart yet. If already authenticated, perform the save immediately and redirect to
   `/cart`.
2. The anonymous cart's entries are already carried through login via this storefront's existing
   cart-merge-on-login behaviour — verify this still holds for the entry in question rather than
   assuming it.
3. A small addition to the cart page's existing GET handler (not a new page): at the top, check for
   the pending session attribute and current authentication state — if both are present, resolve
   the matching (now-merged) cart entry, perform the actual save, and clear the attribute before the
   page renders, so the saved-for-later section is already correct on this render, not a render
   behind.
4. Post-login needs to actually land back on `/cart`, not account home — check whether this
   storefront's login flow already respects the `continueUrl` session-attribute mechanism
   `AbstractCartPageController` uses for its own "continue shopping" link; if so this is a one-line
   `session.setAttribute(WebConstants.CONTINUE_URL, "/cart")` before the redirect in step 1, if not,
   flag it rather than assume it works.

## 6. Acceptance criteria

1. Clicking "Save for later" on a cart entry removes it from the cart and adds it to that
   customer's saved-for-later list.
1a. The "Save for later" button is visible to anonymous shoppers too. Clicking it as an anonymous
    shopper forces a login; once authenticated, the save completes automatically (no second click
    needed) and the shopper lands back on `/cart` with the item already in the saved-for-later
    list, not still in the cart.
2. Saving a product already present in the saved list increments its saved quantity rather than
   creating a duplicate row.
3. The saved-for-later section appears at the bottom of the cart page only — never in the minicart.
4. "Move to cart" adds the item back into the cart via the normal add-to-cart path (merges with an
   existing cart entry of the same product if one exists) and removes it from the saved list.
5. A "Move to cart" that fails (e.g. now out of stock) leaves the item in the saved list and reports
   the failure the same way a normal add-to-cart failure would.
6. "Remove" deletes a saved item without touching the cart.
7. Saved items persist across logout/login for the same customer.
8. A saved item is never shown against a different store's catalog — enforced by filtering on the
   product's own `catalogVersion` at read time, never a hardcoded store id (§5.3).
9. B2B storefront is untouched.
10. Storage stays customer-scoped even for the anonymous flow — the entry is only ever persisted
    after authentication (§5.4), never against a session/anonymous identity.

## 7. Non-goals

- A genuinely session-scoped saved item that never requires login at all (the anonymous *flow*
  above still ends in a login, per §5.4/AC1a).
- A minicart or header indicator for saved items.
- Editing saved-item quantity in place.
- A cap on the number of saved items.

## 8. Open questions

1. ~~Anonymous shoppers~~ — **decided**: the button is visible to everyone; clicking it as an
   anonymous shopper forces a login, and the save completes automatically afterward, landing back
   on `/cart` with the item already saved (§5.4, AC1a). This is real implementation work (a
   pending-action handoff across the login redirect), not a config flip — see §5.4 for the exact
   mechanism and what the implementer needs to verify against this storefront's actual login flow.
2. ~~Item-type vs. hidden-cart design~~ — **decided** (§5.1): a dedicated item type, not the
   existing Saved Carts machinery.
3. ~~`customer` as a plain attribute vs. a relation~~ — **decided** (§5.1a): a real `<relation>`,
   for Backoffice discoverability and customer-data-lifecycle (GDPR deletion) reasons. One thing
   still to verify during implementation, not a design question: whether deleting a `Customer`
   needs an explicit interceptor to clean up their `SavedForLaterEntry` rows, or whether the
   relation handles it — check this platform version's actual behaviour rather than assume either
   way (§5.1a).
4. ~~`baseStore` as a stored attribute~~ — **decided**: dropped. Scoping is enforced by filtering on
   the product's own `catalogVersion` at read time instead (§5.3).
