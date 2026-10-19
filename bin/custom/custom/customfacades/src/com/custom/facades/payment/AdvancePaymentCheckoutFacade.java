/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.payment;

import de.hybris.platform.commercefacades.order.data.PaymentModeData;

import java.util.Optional;


/**
 * Advance payment as a checkout payment option (NET-8942). The shopper pays by bank transfer
 * outside SAP Commerce, so choosing it only records the payment mode and an
 * {@code AdvancePaymentInfo} on the session cart - no payment authorization, no payment
 * transaction.
 * <p>
 * The payment mode is global (NET-8942 section 4, deliberate exception to the multi-store rule):
 * it is switched on and off by its {@code active} flag, not per base store.
 */
public interface AdvancePaymentCheckoutFacade
{
	/** Code of the {@code StandardPaymentMode} created by customcore essential data. */
	String ADVANCE_PAYMENT_MODE_CODE = "advance";

	/**
	 * @return the advance payment mode (code, name, description) if it exists and is active;
	 *         empty if it is missing or inactive - never throws for a missing payment mode
	 */
	Optional<PaymentModeData> getAdvancePaymentMode();

	/**
	 * @return {@code true} if the advance payment mode exists and is active
	 */
	boolean isAdvancePaymentAvailable();

	/**
	 * Chooses advance payment for the session cart: sets the payment mode {@code advance} and a new
	 * {@code AdvancePaymentInfo} (owner and user = the cart's, not saved, billing address = a copy
	 * of the cart's delivery address), replacing the previous payment info. A previous
	 * {@code AdvancePaymentInfo} is removed; a previous credit card payment info is left alone (it
	 * belongs to the customer). The cart is recalculated.
	 *
	 * @return {@code false}, with the cart left unchanged, if advance payment is not available, there
	 *         is no session cart, the current checkout user is not the cart's user, or the cart has
	 *         no delivery address; {@code true} otherwise
	 */
	boolean setAdvancePayment();

	/**
	 * @return {@code true} if the session cart's payment info is an {@code AdvancePaymentInfo}
	 */
	boolean isAdvancePaymentSelected();
}
