/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.payment.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.acceleratorfacades.order.AcceleratorCheckoutFacade;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


/**
 * Unit test for {@link AdvancePaymentAwareCheckoutFlowFacade} (NET-8942 section 5.1): the checkout
 * step validators and the summary controller ask {@code checkoutFlowFacade}, so it must answer the
 * payment questions exactly like the advance-payment-aware checkout facade it forwards to.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class AdvancePaymentAwareCheckoutFlowFacadeTest
{
	@Mock
	private AcceleratorCheckoutFacade checkoutFacadeDelegate;

	private AdvancePaymentAwareCheckoutFlowFacade flowFacade;

	@Before
	public void setUp()
	{
		flowFacade = new AdvancePaymentAwareCheckoutFlowFacade();
		flowFacade.setCheckoutFacadeDelegate(checkoutFacadeDelegate);
	}

	@Test
	public void shouldNotReportMissingPaymentInfoWhenTheDelegateSeesAdvancePayment()
	{
		given(Boolean.valueOf(checkoutFacadeDelegate.hasNoPaymentInfo())).willReturn(Boolean.FALSE);

		assertFalse(flowFacade.hasNoPaymentInfo());
	}

	@Test
	public void shouldReportMissingPaymentInfoWhenTheDelegateDoes()
	{
		given(Boolean.valueOf(checkoutFacadeDelegate.hasNoPaymentInfo())).willReturn(Boolean.TRUE);

		assertTrue(flowFacade.hasNoPaymentInfo());
	}

	@Test
	public void shouldDelegatePaymentAuthorization()
	{
		given(Boolean.valueOf(checkoutFacadeDelegate.authorizePayment("123"))).willReturn(Boolean.TRUE);

		assertTrue(flowFacade.authorizePayment("123"));
		verify(checkoutFacadeDelegate).authorizePayment("123");
	}

	@Test
	public void shouldPassOnARefusedPaymentAuthorization()
	{
		given(Boolean.valueOf(checkoutFacadeDelegate.authorizePayment(null))).willReturn(Boolean.FALSE);

		assertFalse(flowFacade.authorizePayment(null));
	}

	@Test
	public void shouldDelegateChoosingASavedCard()
	{
		given(Boolean.valueOf(checkoutFacadeDelegate.setPaymentDetails("card-1"))).willReturn(Boolean.TRUE);

		assertTrue(flowFacade.setPaymentDetails("card-1"));
		verify(checkoutFacadeDelegate).setPaymentDetails("card-1");
	}

	@Test
	public void shouldDelegateApplyingTheDefaultPaymentInfo()
	{
		given(Boolean.valueOf(checkoutFacadeDelegate.setDefaultPaymentInfoForCheckout())).willReturn(Boolean.TRUE);

		assertTrue(flowFacade.setDefaultPaymentInfoForCheckout());
		verify(checkoutFacadeDelegate).setDefaultPaymentInfoForCheckout();
	}
}
