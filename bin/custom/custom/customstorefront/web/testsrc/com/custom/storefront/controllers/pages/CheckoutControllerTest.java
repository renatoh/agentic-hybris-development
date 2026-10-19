/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.storefront.controllers.pages;

import static org.junit.Assert.assertEquals;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commercefacades.order.data.CCPaymentInfoData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CustomerData;

import org.junit.Before;
import org.junit.Test;


/**
 * Unit test for the guest email lookup of {@link CheckoutController} on the order confirmation page
 * (NET-8942 section 5.3): an advance-payment order has no payment info on {@link OrderData}.
 */
@UnitTest
public class CheckoutControllerTest
{
	private CheckoutController controller;
	private OrderData order;

	@Before
	public void setUp()
	{
		controller = new CheckoutController();

		final CustomerData guest = new CustomerData();
		guest.setUid("4b7f9c1e-guid|guest@example.com");
		order = new OrderData();
		order.setGuestCustomer(true);
		order.setUser(guest);
	}

	@Test
	public void shouldTakeTheGuestEmailFromTheCardBillingAddress()
	{
		final AddressData billingAddress = new AddressData();
		billingAddress.setEmail("card.holder@example.com");
		final CCPaymentInfoData card = new CCPaymentInfoData();
		card.setBillingAddress(billingAddress);
		order.setPaymentInfo(card);

		assertEquals("card.holder@example.com", controller.getGuestEmail(order));
	}

	@Test
	public void shouldTakeTheGuestEmailFromTheUidForAnAdvancePaymentOrder()
	{
		order.setPaymentInfo(null);

		assertEquals("guest@example.com", controller.getGuestEmail(order));
	}

	@Test
	public void shouldTakeTheGuestEmailFromTheUidWhenThePaymentInfoHasNoBillingAddress()
	{
		order.setPaymentInfo(new CCPaymentInfoData());

		assertEquals("guest@example.com", controller.getGuestEmail(order));
	}
}
