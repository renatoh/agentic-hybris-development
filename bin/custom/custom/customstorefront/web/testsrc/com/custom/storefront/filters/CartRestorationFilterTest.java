/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.storefront.filters;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commerceservices.order.CommerceCartRestorationException;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.site.BaseSiteService;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.custom.productservices.exceptions.ServicePriceNotFoundException;
import com.custom.storefront.security.cookie.CartRestoreCookieGenerator;


/**
 * NET-8943 review round 1: a restored cart whose service can no longer be priced ({@link ServicePriceNotFoundException})
 * must not break the page — it is reported like a failed restoration. Any other runtime failure is not swallowed.
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class CartRestorationFilterTest
{
	private static final String COOKIE_NAME = "cartRestoreCookie";
	private static final String CART_GUID = "anonymous-cart-guid";

	@Mock
	private UserService userService;
	@Mock
	private CartService cartService;
	@Mock
	private CartFacade cartFacade;
	@Mock
	private BaseSiteService baseSiteService;
	@Mock
	private SessionService sessionService;
	@Mock
	private CartRestoreCookieGenerator cartRestoreCookieGenerator;
	@Mock
	private HttpServletRequest request;

	private CartRestorationFilter filter;

	@Before
	public void setUp()
	{
		filter = new CartRestorationFilter();
		filter.setUserService(userService);
		filter.setCartService(cartService);
		filter.setCartFacade(cartFacade);
		filter.setBaseSiteService(baseSiteService);
		filter.setSessionService(sessionService);
		filter.setCartRestoreCookieGenerator(cartRestoreCookieGenerator);
	}

	/** logged-in user without a session cart and no restoration attempted yet in this session */
	private void givenALoggedInUserWithoutSessionCart()
	{
		given(Boolean.valueOf(cartService.hasSessionCart())).willReturn(Boolean.FALSE);
		given(sessionService.getAttribute(WebConstants.CART_RESTORATION)).willReturn(null);
	}

	/** anonymous user whose browser carries the cart restoration cookie */
	private void givenTheCartRestorationCookie()
	{
		given(cartRestoreCookieGenerator.getCookieName()).willReturn(COOKIE_NAME);
		given(request.getCookies()).willReturn(new Cookie[]
		{ new Cookie("other", "x"), new Cookie(COOKIE_NAME, CART_GUID) });
	}

	// --- logged-in user: restoreCartWithNoCode -------------------------------------------------------------------

	@Test
	public void shouldReportAnUnpricedServiceAsFailedRestorationForALoggedInUser() throws Exception
	{
		givenALoggedInUserWithoutSessionCart();
		given(cartFacade.restoreSavedCart(null)).willThrow(new ServicePriceNotFoundException("no price for SRV_INSTALL"));

		filter.restoreCartWithNoCode();

		verify(sessionService).setAttribute(WebConstants.CART_RESTORATION, WebConstants.CART_RESTORATION_ERROR_STATUS);
	}

	@Test
	public void shouldStillReportACommerceRestorationFailureForALoggedInUser() throws Exception
	{
		givenALoggedInUserWithoutSessionCart();
		given(cartFacade.restoreSavedCart(null)).willThrow(new CommerceCartRestorationException("cannot restore"));

		filter.restoreCartWithNoCode();

		verify(sessionService).setAttribute(WebConstants.CART_RESTORATION, WebConstants.CART_RESTORATION_ERROR_STATUS);
	}

	@Test
	public void shouldPropagateAnyOtherRuntimeFailureForALoggedInUser() throws Exception
	{
		givenALoggedInUserWithoutSessionCart();
		final IllegalStateException failure = new IllegalStateException("unexpected");
		given(cartFacade.restoreSavedCart(null)).willThrow(failure);

		try
		{
			filter.restoreCartWithNoCode();
			fail("expected the IllegalStateException to propagate");
		}
		catch (final IllegalStateException e)
		{
			assertSame(failure, e);
		}
		verify(sessionService, never()).setAttribute(WebConstants.CART_RESTORATION, WebConstants.CART_RESTORATION_ERROR_STATUS);
	}

	// --- anonymous user: processRestoration ----------------------------------------------------------------------

	@Test
	public void shouldReportAnUnpricedServiceAsFailedRestorationForAnAnonymousUser() throws Exception
	{
		givenTheCartRestorationCookie();
		given(cartFacade.restoreSavedCart(CART_GUID)).willThrow(new ServicePriceNotFoundException("no price for SRV_INSTALL"));

		filter.processRestoration(request);

		verify(sessionService).setAttribute(WebConstants.CART_RESTORATION_ERROR_STATUS,
				WebConstants.CART_RESTORATION_ERROR_STATUS);
	}

	@Test
	public void shouldStillReportACommerceRestorationFailureForAnAnonymousUser() throws Exception
	{
		givenTheCartRestorationCookie();
		given(cartFacade.restoreSavedCart(CART_GUID)).willThrow(new CommerceCartRestorationException("cannot restore"));

		filter.processRestoration(request);

		verify(sessionService).setAttribute(WebConstants.CART_RESTORATION_ERROR_STATUS,
				WebConstants.CART_RESTORATION_ERROR_STATUS);
	}

	@Test
	public void shouldPropagateAnyOtherRuntimeFailureForAnAnonymousUser() throws Exception
	{
		givenTheCartRestorationCookie();
		final IllegalStateException failure = new IllegalStateException("unexpected");
		given(cartFacade.restoreSavedCart(CART_GUID)).willThrow(failure);

		try
		{
			filter.processRestoration(request);
			fail("expected the IllegalStateException to propagate");
		}
		catch (final IllegalStateException e)
		{
			assertSame(failure, e);
		}
		verify(sessionService, never()).setAttribute(WebConstants.CART_RESTORATION_ERROR_STATUS,
				WebConstants.CART_RESTORATION_ERROR_STATUS);
	}
}
