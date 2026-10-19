/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.productservices.cart;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.InvalidCartException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


/**
 * NET-8943 &sect;5.4, AC10, AC11: an order is never placed with a service entry that is no longer valid (it could not
 * be priced correctly). The check itself is read-only: it must not alter the cart.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class ServiceValidationPlaceOrderMethodHookTest
{
	@Mock
	private CartServiceSelectionService cartServiceSelectionService;

	private ServiceValidationPlaceOrderMethodHook hook;
	private CartModel cart;
	private CommerceCheckoutParameter parameter;

	@Before
	public void setUp()
	{
		hook = new ServiceValidationPlaceOrderMethodHook();
		hook.setCartServiceSelectionService(cartServiceSelectionService);
		cart = new CartModel();
		cart.setCode("cart-1");
		parameter = new CommerceCheckoutParameter();
		parameter.setCart(cart);
	}

	@Test
	public void shouldRejectPlacingAnOrderWithAnInvalidService()
	{
		given(Boolean.valueOf(cartServiceSelectionService.hasInvalidServices(cart))).willReturn(Boolean.TRUE);

		try
		{
			hook.beforePlaceOrder(parameter);
			fail("expected InvalidCartException");
		}
		catch (final InvalidCartException expected)
		{
			assertTrue(expected.getMessage().contains("cart-1"));
		}
		verify(cartServiceSelectionService).hasInvalidServices(cart);
		verifyNoMoreInteractions(cartServiceSelectionService);
	}

	@Test
	public void shouldAllowPlacingAnOrderWhenEveryServiceIsValid() throws InvalidCartException
	{
		given(Boolean.valueOf(cartServiceSelectionService.hasInvalidServices(cart))).willReturn(Boolean.FALSE);

		hook.beforePlaceOrder(parameter);

		verify(cartServiceSelectionService).hasInvalidServices(cart);
		verifyNoMoreInteractions(cartServiceSelectionService);
	}

	@Test
	public void shouldDoNothingWithoutParameterOrCart() throws InvalidCartException
	{
		hook.beforePlaceOrder(null);
		hook.beforePlaceOrder(new CommerceCheckoutParameter());

		verifyNoInteractions(cartServiceSelectionService);
	}

	@Test
	public void shouldDoNothingAfterPlacingOrBeforeSubmittingTheOrder()
	{
		hook.afterPlaceOrder(parameter, new CommerceOrderResult());
		hook.beforeSubmitOrder(parameter, new CommerceOrderResult());

		verifyNoInteractions(cartServiceSelectionService);
	}
}
