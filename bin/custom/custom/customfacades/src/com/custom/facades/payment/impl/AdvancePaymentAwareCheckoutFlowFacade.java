/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.payment.impl;

import de.hybris.platform.acceleratorfacades.flow.impl.SessionOverrideCheckoutFlowFacade;
import de.hybris.platform.acceleratorfacades.order.AcceleratorCheckoutFacade;

import org.springframework.beans.factory.annotation.Required;


/**
 * Takes over the {@code checkoutFlowFacade} alias (NET-8942). The checkout step validators and
 * controllers ask this bean - not {@code acceleratorCheckoutFacade} - whether the cart has a payment
 * info ({@code hasNoPaymentInfo()}), and the platform only recognises credit cards there. Rather than
 * duplicating the advance-payment handling of {@link DefaultAdvancePaymentCheckoutFacade}, the
 * payment-related methods are forwarded to that bean, so both checkout facades give the same answers.
 */
public class AdvancePaymentAwareCheckoutFlowFacade extends SessionOverrideCheckoutFlowFacade
{
	private AcceleratorCheckoutFacade checkoutFacadeDelegate;

	@Override
	public boolean hasNoPaymentInfo()
	{
		return getCheckoutFacadeDelegate().hasNoPaymentInfo();
	}

	@Override
	public boolean authorizePayment(final String securityCode)
	{
		return getCheckoutFacadeDelegate().authorizePayment(securityCode);
	}

	@Override
	public boolean setPaymentDetails(final String paymentInfoId)
	{
		return getCheckoutFacadeDelegate().setPaymentDetails(paymentInfoId);
	}

	@Override
	public boolean setDefaultPaymentInfoForCheckout()
	{
		return getCheckoutFacadeDelegate().setDefaultPaymentInfoForCheckout();
	}

	protected AcceleratorCheckoutFacade getCheckoutFacadeDelegate()
	{
		return checkoutFacadeDelegate;
	}

	@Required
	public void setCheckoutFacadeDelegate(final AcceleratorCheckoutFacade checkoutFacadeDelegate)
	{
		this.checkoutFacadeDelegate = checkoutFacadeDelegate;
	}
}
