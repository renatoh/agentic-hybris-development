# NET-8942 — Advance payment as a payment option

| | |
|---|---|
| **Ticket** | NET-8942 |
| **Status** | Agreed for implementation (2026-10-19). §8 questions 4–6 follow their draft answers until decided otherwise. |
| **Extension** | `customcore` (ImpEx only) + `customfacades` (facade/DTOs) + `customstorefront` (checkout web layer) |
| **Platform** | SAP Commerce Cloud 2211.37, storefront `customstorefront` (B2C) |

## 0. Original request (verbatim)

> right now it looks like I cannot checkout withotu choosing a credit card as payment. we need to
> work on a new requirement for advanced payment as payment option. this does not require any
> payment handling within sap commerce.

Follow-up decisions (verbatim):

> ok, I really want to keep to keep it simple. first, we do not need to store and presetnt any
> bank-information. seconldy, for now we have only one store, so we just use the existing
> paymentmode without a mapping from paymentmode to basestore.

## 1. Context

### 1.1 The checkout only knows credit cards

The multi-step checkout of `customstorefront` (delivery address → delivery method → payment
method → summary) assumes a credit card in four places:

| Where | What it assumes |
|---|---|
| `PaymentMethodCheckoutStepController` + payment step JSP | The only way to complete the step is to enter or pick a card (SOP/HOP form). |
| `SummaryCheckoutStepController.validateOrderForm` | `checkoutFlowFacade.hasNoPaymentInfo()` must be false, and with the PCI option `DEFAULT` a card security code must be entered. |
| `SummaryCheckoutStepController.placeOrder` → `DefaultCheckoutFacade.authorizePayment` | **Casts** `cart.paymentInfo` to `CreditCardPaymentInfoModel` and needs a `subscriptionId`; if authorization returns false the order is refused (`checkout.error.authorization.failed`). Any other payment info type would fail here with a `ClassCastException`. |
| `multi-checkout:paymentInfo` tag (summary, order confirmation, order history) | Renders card fields only (holder, card type, number, expiry). |

`DefaultCommercePlaceOrderStrategy` itself does not require a card: it copies the cart's payment
info (and its billing address) to the order and does not check the type.

### 1.2 What the platform already has

Everything needed on the data side exists already — **no `items.xml` change**:

- **`AdvancePaymentInfo`** (`core-items.xml`): a `PaymentInfo` subtype without extra attributes.
- **`PaymentMode` / `StandardPaymentMode`** (`core`, `paymentstandard`): `code`, localized `name`
  and `description`, an `active` flag, `paymentInfoType` (the `PaymentInfo` subtype that belongs to
  the mode) and optional costs per currency (`StandardPaymentModeValue`).
- **`AbstractOrder.paymentMode`**: carts and orders can record their payment mode next to
  `paymentInfo`.
- **`PaymentModeService`**: `getPaymentModeForCode(code)`, `getAllPaymentModes()`.

Payment modes are **global** in the platform: `BaseStore` has no link to payment modes (unlike
delivery modes, `BaseStore2DeliveryModeRel`), and `getAllPaymentModes()` returns every payment mode
without checking a store or even the `active` flag.

### 1.3 No order process

`customfulfilmentprocess` / `order-process` are not active in this installation (see NET-8943 §9),
so no order process checks payment after the order is placed.

## 2. Goal

At the payment step the shopper can choose **"Advance payment"** instead of a credit card. The
order is then placed **without any payment authorization or payment transaction in SAP Commerce**.
Collecting the money (bank details, reminders, matching incoming payments) happens entirely outside
SAP Commerce.

Credit card stays available as before.

## 3. Scope

**In scope**

- A payment mode `advance` (the platform's `StandardPaymentMode`, `paymentInfoType =
  AdvancePaymentInfo`), created by ImpEx.
- A payment-method choice on the payment step: credit card (existing behaviour) or advance payment.
- Placing an order with advance payment: no security code, no authorization, no payment transaction.
- Showing "Advance payment" instead of card details on the checkout summary, the order confirmation
  page and the order details in My Account → Order History.

**Out of scope**

- **Bank details or payment instructions** — not stored and not shown anywhere (decision, §0).
- **A per-store switch** — the payment mode is global; there is one store today (decision, §0).
- Tracking incoming payments, payment reminders, cancelling unpaid orders, changing the order
  status when the money arrives.
- Express checkout (it always uses the customer's default card); advance payment is not offered there.
- OCC / Commerce Web Services checkout.
- The B2B storefront.
- Order confirmation email content.
- Payment fees or discounts for advance payment.

## 4. Data (ImpEx only)

No new item types or attributes.

ImpEx in `customcore` essential data, registered through the existing `SystemSetup` pattern so it
runs under `ant updatesystem` and a future `ant initialize`:

```impex
INSERT_UPDATE StandardPaymentMode; code[unique = true]; name[lang = en]; description[lang = en]; active; paymentInfoType(code)
; advance ; Advance payment ; Pay in advance by bank transfer. Your order is processed once the payment has arrived. ; true ; AdvancePaymentInfo

INSERT_UPDATE StandardPaymentModeValue; paymentMode(code)[unique = true]; currency(isocode)[unique = true]; value
; advance ; <currency> ; 0
```

- One `StandardPaymentModeValue` with value `0` per currency the store sells in (the implementer
  takes the list from the store's data), so that calculating an order with this payment mode
  never fails or adds a cost (§5.4).
- English only (CLAUDE.md).

**Switching it on and off:** the `active` flag of the `advance` payment mode (Backoffice → Payment
Modes). It applies to the whole installation.

**Deliberate exception to the multi-store rule** (CLAUDE.md: "Design for multiple stores"): the
payment mode is global and not linked to a store, as decided in §0. If a second store needs a
different set of payment modes later, the extension point is a `BaseStore2PaymentModeRel` relation
(same pattern as `BaseStore2DeliveryModeRel`) — not part of this ticket.

## 5. Design

### 5.1 Payment step

- The payment step shows a choice: **Credit card** / **Advance payment** (radio buttons above the
  existing card form). Advance payment is shown only when the payment mode `advance` exists and is
  `active`. Its label and description come from the payment mode's `name` and `description`.
- Looking it up: `PaymentModeService.getPaymentModeForCode("advance")` behind a facade method, with
  a missing payment mode treated as "not offered" (no exception reaching the page). Do **not** use
  `getAllPaymentModes()` for this — it ignores `active`.
- Choosing credit card keeps today's behaviour unchanged (SOP/HOP form, saved cards).
- Choosing advance payment hides the card form, shows the payment mode's description and a "Next"
  button that posts to a new endpoint on the payment step controller.
- That endpoint calls a facade method that, on the session cart:
  - sets `paymentMode` = `advance`;
  - sets a new `AdvancePaymentInfo`: owner = cart, user = cart user, `saved = false` (never appears
    as a saved payment method in My Account), billing address = a copy of the cart's delivery
    address (the platform copies the billing address to the order, §1.1);
  - **(decided during implementation)** if the copied address has no email, it gets the customer's
    email (`CustomerEmailResolutionService`, guests included). A card's billing address gets its
    email from the payment form; a delivery address usually has none, and the billing address
    becomes the order's payment address;
  - replaces an earlier payment info; an earlier `AdvancePaymentInfo` that is no longer referenced
    is removed (no orphans);
  - recalculates the cart if the payment mode change requires it.
- Choosing a card after advance payment clears `paymentMode` and removes the `AdvancePaymentInfo`
  the same way. Card payments keep using no payment mode, as today.
  - **(review finding, fixed)** The mode is cleared on *every* successful card selection whenever it
    is `advance`, not only when the previous payment info was an `AdvancePaymentInfo`: the guest
    checkout cart clean (`DefaultGuestCheckoutCartCleanStrategy`, run when a guest leaves the
    checkout) drops the payment info. That clean now also clears `paymentMode` and removes a dropped
    `AdvancePaymentInfo` with its billing address.
- **(review finding, fixed)** The billing address is a copy of the delivery address taken when
  advance payment is chosen. Because the shopper can change the delivery address afterwards and
  jump to the summary via the progress bar, it is copied again right before the order is placed
  (`beforePlaceOrder`), so the order's payment address always equals its delivery address.
- **(review finding, open - not changed)** After the order is placed, the cart's `AdvancePaymentInfo`
  (the `original` of the order's cloned payment info) and its billing address stay in the database
  once the cart is removed - one per advance-payment order, holding the customer's address and
  email. The platform does the same for cards, where the original is the customer's saved card.
  Removing it needs care because `PaymentInfo.original` is initial-only. To be decided; any
  personal-data retention/erasure cleanup must cover these items.
- ~~With the cart's payment info set, `hasNoPaymentInfo()` is false~~ — **verified false against the
  code, adjusted (decided 2026-10-19):** `hasNoPaymentInfo()` looks at `CartData.paymentInfo`, which
  the platform fills only for a `CreditCardPaymentInfo` (`DefaultCheckoutFacade.getPaymentDetails`,
  `AbstractOrderPopulator.addPaymentInformation`). So:
  - the custom checkout facade (`DefaultAdvancePaymentCheckoutFacade`, alias
    `acceleratorCheckoutFacade`) overrides `hasNoPaymentInfo()` to also accept an advance-payment
    cart: an `AdvancePaymentInfo` **and** the cart's payment mode `advance`;
  - the step validators and the summary controller ask `checkoutFlowFacade`, not
    `acceleratorCheckoutFacade`. **`checkoutFlowFacade` is replaced too** (accepted by the user):
    `AdvancePaymentAwareCheckoutFlowFacade` extends the platform's `SessionOverrideCheckoutFlowFacade`
    and forwards `hasNoPaymentInfo`, `authorizePayment`, `setPaymentDetails` and
    `setDefaultPaymentInfoForCheckout` to the custom checkout facade, so both beans answer the same.

### 5.2 Summary and placing the order

- `validateOrderForm`: the security-code check only applies to card payment infos. With an
  `AdvancePaymentInfo` no security code is asked for or required (the field is not shown).
- `placeOrder`: payment authorization is **skipped** for an `AdvancePaymentInfo` — no call to the
  payment service, no `PaymentTransaction`. Card orders keep the existing authorization path
  unchanged.
- Where to put the switch: in the facade layer (a custom `CheckoutFacade` bean in `customfacades`
  overriding `authorizePayment` to return `true` for an `AdvancePaymentInfo` without creating a
  transaction, and delegating to the platform otherwise), not only as an `if` in the controller —
  so the platform cast in `DefaultCheckoutFacade.authorizePayment` is never reached with a non-card
  payment info. Upgrade check: compare with `DefaultCheckoutFacade.authorizePayment` on every upgrade.
- Before placing the order, check that the cart's payment mode `advance` is still `active`; if it
  was switched off meanwhile, refuse with a message and send the shopper back to the payment step.
- The rest of `placeOrder` is unchanged (cart validation, terms check, `placeOrder()`).
- The order keeps the platform's normal initial status; no new order status. The order carries
  `paymentMode = advance` and the `AdvancePaymentInfo`, so advance-payment orders can be found by
  either.

### 5.3 Display

- **Decided 2026-10-19 (order-level approach).** There is no order-level `PaymentInfoData` in the
  platform: `AbstractOrderData.paymentInfo` is a `CCPaymentInfoData`, filled only for credit cards.
  A `paymentType` field on `CCPaymentInfoData` was tried and **rejected**. Instead:
  - for advance payment `CartData`/`OrderData.paymentInfo` stays **empty**, as the platform does for
    any non-card payment info;
  - `AbstractOrderData` gets a field `paymentMode` (`PaymentModeData`: code, name, description),
    declared in `customfacades-beans.xml` and filled by a populator (`PaymentModeOrderPopulator`) on
    the cart and order converters (`cartConverter`, `extendedCartConverter` - the one the checkout
    facades read the cart through - and `orderConverter`) from the cart's/order's `paymentMode`. Card carts/orders have no
    payment mode, so the field stays empty for them;
  - the label shown is the payment mode's `name` ("Advance payment").
- `multi-checkout:paymentInfo` (summary) and the order details tag used for the order confirmation
  and My Account render the payment block when `paymentInfo` **or** `paymentMode` is set: card
  fields as before, otherwise the payment mode's name. The billing-address block on the order
  details is shown only with a card `paymentInfo` (advance payment has none on the DTO). No bank
  details, no amount or payment reference beyond what the pages already show (order number, totals).
- Guest order confirmation: `CheckoutController` took the guest's email from
  `paymentInfo.billingAddress.email`, which would fail with an empty `paymentInfo`. Without a card
  payment info it now takes the email from the guest's uid (`<guid>|<email>`), the format the same
  controller already parses.
- New texts English only, in `base_en.properties`.

### 5.4 Payment cost

With `paymentMode` set, the platform's order calculation includes the payment mode's cost. For
`advance` that must be `0` and must not fail. The implementer verifies this with a real calculation
of a cart with `paymentMode = advance` in the store's currency, and also what happens for a
currency without a `StandardPaymentModeValue` (§4 provides one per store currency).

### 5.5 Interplay with existing features

- NET-8943 (services sold with products): no special handling; the order total includes services
  as today.
- NET-8941 (save for later): unaffected.
- Saved cards in My Account → Payment Details: unaffected; `AdvancePaymentInfo` is never listed
  there (`saved = false`, and the platform only lists credit card payment infos).

## 6. Acceptance criteria

1. With the payment mode `advance` active, the payment step offers "Credit card" and
   "Advance payment". With it inactive or missing, only the card form is shown, exactly as today.
2. Choosing advance payment and continuing leads to the summary page without entering any card data.
3. The summary shows "Advance payment" as payment method and asks for no security code.
4. Placing the order succeeds; the order has `paymentMode = advance`, an `AdvancePaymentInfo`
   (billing address = delivery address), **no** `PaymentTransaction`, and no payment cost added
   to its total.
5. The order confirmation page and My Account → Order History → order details show
   "Advance payment" as payment method, and no card details.
6. Credit card checkout works exactly as before (including authorization and the security code
   when the PCI option requires it); card orders have no payment mode.
7. Switching from advance payment back to card (or the other way) during checkout leaves exactly
   one payment info on the cart, the matching payment mode (or none for card), and no orphaned
   `AdvancePaymentInfo` items.
8. `AdvancePaymentInfo` never shows up under My Account → Payment Details.
9. Posting the advance-payment endpoint while the payment mode is inactive is rejected; the cart
   keeps its previous payment info. Placing an order after the mode was switched off is refused.
10. The `advance` payment mode ImpEx runs under `ant updatesystem` and is registered so it also
    runs under `ant initialize`.
11. Unit tests cover the facade (offering the mode, setting/replacing the payment info and mode,
    authorization skip), the populator, and the controller paths for both payment types.

## 7. Non-goals

- Any payment processing, reconciliation or status update for advance payment in SAP Commerce.
- Bank details or payment instructions anywhere.
- Per-store payment modes.
- Showing advance payment in express checkout or OCC.
- Making advance payment the default selection.

## 8. Open questions

1. ~~Ticket id~~ — **decided**: NET-8942.
2. ~~Bank details~~ — **decided**: not stored, not shown (§0).
3. ~~Per-store switch~~ — **decided**: global payment mode, no mapping to the base store (§0, §4).
4. **Who may use it** — every shopper who can check out (draft), or only registered customers?
   Does it apply to guest checkout too, if guest checkout is enabled?
5. **Fulfilment** — if `customfulfilmentprocess`/`order-process` is activated later, its payment
   check would treat advance-payment orders as unauthorized. Accept that for now (draft) or design
   a "wait for payment" step now?
6. **Default selection** — should the payment step pre-select credit card (draft) or nothing?
