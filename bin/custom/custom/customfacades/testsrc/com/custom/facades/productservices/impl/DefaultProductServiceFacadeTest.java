/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.facades.productservices.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.session.SessionService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.constants.CustomservicesConstants;
import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.cart.CartServiceSelectionService;
import com.custom.productservices.exceptions.CartServiceSelectionException;
import com.custom.productservices.service.impl.DefaultServiceEntryGroupService;


/**
 * NET-8943 &sect;5.4/&sect;5.7, AC11: the cart page shows the shopper every service removed as invalid - those a
 * calculation removed earlier in the session and those removed now - exactly once, and the cart always loads.
 * <p>
 * Never creates a session cart as a side effect: {@code getSessionCart()} is only called when one exists.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultProductServiceFacadeTest
{
	private static final String PENDING = CustomservicesConstants.REMOVED_SERVICES_SESSION_ATTRIBUTE;

	@Mock
	private CartService cartService;
	@Mock
	private SessionService sessionService;
	@Mock
	private CartServiceSelectionService cartServiceSelectionService;

	private DefaultProductServiceFacade facade;
	private CartModel cart;

	@Before
	public void setUp()
	{
		facade = new DefaultProductServiceFacade();
		facade.setCartService(cartService);
		facade.setSessionService(sessionService);
		facade.setCartServiceSelectionService(cartServiceSelectionService);
		facade.setServiceEntryGroupService(new DefaultServiceEntryGroupService());

		cart = new CartModel();
		cart.setEntries(new ArrayList<>());
	}

	private static ServiceProductModel serviceNamed(final String name)
	{
		// a mock only because the localized getName needs a session context
		final ServiceProductModel service = mock(ServiceProductModel.class);
		given(service.getName()).willReturn(name);
		return service;
	}

	private void givenSessionCart()
	{
		given(Boolean.valueOf(cartService.hasSessionCart())).willReturn(Boolean.TRUE);
		given(cartService.getSessionCart()).willReturn(cart);
	}

	private void givenPending(final String... names)
	{
		given(sessionService.getAttribute(PENDING)).willReturn(new ArrayList<>(Arrays.asList(names)));
	}

	private CartEntryModel entry(final int entryNumber, final ProductModel product)
	{
		final CartEntryModel entry = new CartEntryModel();
		entry.setEntryNumber(Integer.valueOf(entryNumber));
		entry.setProduct(product);
		entry.setOrder(cart);
		final List<AbstractOrderEntryModel> entries = new ArrayList<>(cart.getEntries());
		entries.add(entry);
		cart.setEntries(entries);
		return entry;
	}

	// --- removeInvalidServicesFromCart ----------------------------------------------------------------------------

	@Test
	public void shouldReturnPendingNamesFirstThenTheOnesRemovedNowAndClearThePending()
	{
		givenPending("Installation");
		givenSessionCart();
		final ServiceProductModel warranty = serviceNamed("3-year warranty");
		given(cartServiceSelectionService.removeInvalidServices(cart)).willReturn(Collections.singletonList(warranty));

		assertEquals(Arrays.asList("Installation", "3-year warranty"), facade.removeInvalidServicesFromCart());

		verify(sessionService).removeAttribute(PENDING);
	}

	@Test
	public void shouldReturnOnlyTheServicesRemovedNowWhenNothingIsPending()
	{
		givenSessionCart();
		final ServiceProductModel installation = serviceNamed("Installation");
		given(cartServiceSelectionService.removeInvalidServices(cart)).willReturn(Collections.singletonList(installation));

		assertEquals(Collections.singletonList("Installation"), facade.removeInvalidServicesFromCart());

		verify(sessionService, never()).removeAttribute(anyString());
	}

	@Test
	public void shouldReturnOnlyThePendingNamesWithoutASessionCartAndNotCreateOne()
	{
		givenPending("Installation", "3-year warranty");
		given(Boolean.valueOf(cartService.hasSessionCart())).willReturn(Boolean.FALSE);

		assertEquals(Arrays.asList("Installation", "3-year warranty"), facade.removeInvalidServicesFromCart());

		verify(sessionService).removeAttribute(PENDING);
		verify(cartService, never()).getSessionCart();
		verifyNoInteractions(cartServiceSelectionService);
	}

	@Test
	public void shouldStillReturnThePendingNamesWhenTheCleanupFails()
	{
		givenPending("Installation");
		givenSessionCart();
		willThrow(new IllegalStateException("cleanup failed")).given(cartServiceSelectionService).removeInvalidServices(cart);

		assertEquals(Collections.singletonList("Installation"), facade.removeInvalidServicesFromCart());

		verify(sessionService).removeAttribute(PENDING);
	}

	@Test
	public void shouldReturnNothingForACleanCart()
	{
		givenSessionCart();
		given(cartServiceSelectionService.removeInvalidServices(cart)).willReturn(Collections.emptyList());

		assertTrue(facade.removeInvalidServicesFromCart().isEmpty());
	}

	@Test
	public void shouldReturnThePendingNamesOnlyOnce()
	{
		given(Boolean.valueOf(cartService.hasSessionCart())).willReturn(Boolean.FALSE);
		given(sessionService.getAttribute(PENDING)).willReturn(new ArrayList<>(Collections.singletonList("Installation")))
				.willReturn(null);

		assertEquals(Collections.singletonList("Installation"), facade.removeInvalidServicesFromCart());
		assertTrue("the second page load shows nothing again", facade.removeInvalidServicesFromCart().isEmpty());
	}

	// --- isServiceEntry -------------------------------------------------------------------------------------------

	@Test
	public void shouldRecogniseAServiceEntryOfTheSessionCart()
	{
		givenSessionCart();
		entry(0, new ProductModel());
		entry(1, new ServiceProductModel());

		assertTrue(facade.isServiceEntry(1));
		assertFalse(facade.isServiceEntry(0));
		assertFalse("unknown entry number", facade.isServiceEntry(7));
	}

	@Test
	public void shouldNotTreatAnythingAsAServiceEntryWithoutASessionCartAndNotCreateOne()
	{
		given(Boolean.valueOf(cartService.hasSessionCart())).willReturn(Boolean.FALSE);

		assertFalse(facade.isServiceEntry(0));

		verify(cartService, never()).getSessionCart();
	}

	@Test
	public void shouldNotTreatAnythingAsAServiceEntryInACartWithoutEntries()
	{
		givenSessionCart();
		cart.setEntries(null);

		assertFalse(facade.isServiceEntry(0));
	}

	// --- add / remove ---------------------------------------------------------------------------------------------

	@Test
	public void shouldAddAndRemoveServicesOnTheSessionCart() throws CartServiceSelectionException
	{
		given(cartService.getSessionCart()).willReturn(cart);

		facade.addServiceToCart(0, "SVC_INSTALLATION");
		facade.removeServiceFromCart(0, "SVC_WARRANTY_3Y");

		verify(cartServiceSelectionService).addService(cart, 0, "SVC_INSTALLATION");
		verify(cartServiceSelectionService).removeService(cart, 0, "SVC_WARRANTY_3Y");
	}

	@Test(expected = CartServiceSelectionException.class)
	public void shouldPassOnAFailureToAddAService() throws CartServiceSelectionException
	{
		given(cartService.getSessionCart()).willReturn(cart);
		willThrow(new CartServiceSelectionException("not offered")).given(cartServiceSelectionService).addService(cart, 0,
				"SVC_INSTALLATION");

		facade.addServiceToCart(0, "SVC_INSTALLATION");
	}
}
