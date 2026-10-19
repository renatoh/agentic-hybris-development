/*
 * Copyright (c) 2021 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.storefront.security.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import de.hybris.bootstrap.annotations.UnitTest;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.commerceservices.strategies.impl.DefaultCheckoutCustomerStrategy;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.delivery.DeliveryModeModel;
import de.hybris.platform.core.model.order.payment.AdvancePaymentInfoModel;
import de.hybris.platform.core.model.order.payment.CreditCardPaymentInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentModeModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


/**
 * Unit Test for {@link DefaultGuestCheckoutCartCleanStrategy}
 *
 */
@UnitTest
@RunWith(MockitoJUnitRunner.class)
public class DefaultGuestCheckoutCartCleanStrategyTest
{
	private static final String CHECKOUT_URL_PATTERN = "(^https://.*/checkout/.*)";
	private static final String FAVICON_PATTERN = "(^https://.*/favicon.ico.*)";
	private static final String HTTP_REQUEST_GET_METHOD = "GET";
	private static final String HTTP_REQUEST_POST_METHOD = "POST";
	public static final String AJAX_REQUEST_HEADER_NAME = "X-Requested-With";

	@Mock
	private HttpServletRequest request;
	@Mock
	private HttpSession session;
	@Mock
	private HttpSession httpSession;
	@Mock
	private FilterChain filterChain;
	@Mock
	private UserService userService;
	@Mock
	private CustomerModel customer;
	@Mock
	private CustomerModel guestCustomer;
	@Mock
	private DefaultCheckoutCustomerStrategy checkoutCustomerStrategy;
	@Mock
	private CartService cartService;
	@Mock
	private SessionService sessionService;
	@Mock
	private ModelService modelService;
	@InjectMocks
	private final DefaultGuestCheckoutCartCleanStrategy guestCheckoutCartCleanStrategy = new DefaultGuestCheckoutCartCleanStrategy();


	@Before
	public void prepare()
	{
		guestCheckoutCartCleanStrategy.setSkipPatterns(Arrays.asList(CHECKOUT_URL_PATTERN, FAVICON_PATTERN));
	}

	@Test
	public void testForGuestAbandonAnonymousCheckout() throws IOException, ServletException
	{

		given(sessionService.getAttribute(WebConstants.ANONYMOUS_CHECKOUT)).willReturn(Boolean.TRUE);
		given(Boolean.valueOf(checkoutCustomerStrategy.isAnonymousCheckout())).willReturn(Boolean.TRUE);
		given(request.getMethod()).willReturn(HTTP_REQUEST_GET_METHOD);
		given(request.getRequestURL()).willReturn(new StringBuffer(
				"https://electronics.local:9002/customstorefront/electronics/en/Open-Catalogue/Cameras/Digital-Cameras/Digital-Compacts/PowerShot-A480/p/1934793"));

		final CartModel cartModel = mock(CartModel.class);
		final AddressModel addressModel = mock(AddressModel.class);
		final DeliveryModeModel deliveryModeModel = mock(DeliveryModeModel.class);
		final PaymentInfoModel paymentModeModel = mock(PaymentInfoModel.class);
		cartModel.setDeliveryAddress(addressModel);
		cartModel.setDeliveryMode(deliveryModeModel);
		cartModel.setPaymentInfo(paymentModeModel);
		given(cartService.getSessionCart()).willReturn(cartModel);
		guestCheckoutCartCleanStrategy.cleanGuestCart(request);

		verify(cartModel).setDeliveryAddress(null);
		verify(cartModel).setDeliveryMode(null);
		verify(cartModel).setPaymentInfo(null);
		verify(cartModel).setUser(userService.getAnonymousUser());
		verify(sessionService).removeAttribute(WebConstants.ANONYMOUS_CHECKOUT);
		verify(sessionService).removeAttribute(WebConstants.ANONYMOUS_CHECKOUT_GUID);
	}


	@Test
	public void testForGuestInAnonymousCheckout() throws IOException, ServletException
	{
		given(sessionService.getAttribute(WebConstants.ANONYMOUS_CHECKOUT)).willReturn(Boolean.TRUE);
		given(Boolean.valueOf(checkoutCustomerStrategy.isAnonymousCheckout())).willReturn(Boolean.TRUE);
		given(request.getMethod()).willReturn(HTTP_REQUEST_GET_METHOD);
		given(request.getRequestURL())
				.willReturn(new StringBuffer("https://electronics.local:9002/customstorefront/electronics/en/checkout/multi"));

		final CartModel cartModel = mock(CartModel.class);
		guestCheckoutCartCleanStrategy.cleanGuestCart(request);

		verifyNoMoreInteractions(cartModel);
	}

	@Test
	public void testForGuestInAnonymousCheckoutForPostRequest() throws IOException, ServletException
	{
		given(sessionService.getAttribute(WebConstants.ANONYMOUS_CHECKOUT)).willReturn(Boolean.TRUE);
		given(Boolean.valueOf(checkoutCustomerStrategy.isAnonymousCheckout())).willReturn(Boolean.TRUE);
		given(request.getMethod()).willReturn(HTTP_REQUEST_POST_METHOD);

		final CartModel cartModel = mock(CartModel.class);
		guestCheckoutCartCleanStrategy.cleanGuestCart(request);

		verifyNoMoreInteractions(cartModel);
	}

	@Test
	public void testForGuestInAnonymousCheckoutForAjaxRequest() throws IOException, ServletException
	{
		given(sessionService.getAttribute(WebConstants.ANONYMOUS_CHECKOUT)).willReturn(Boolean.TRUE);
		given(Boolean.valueOf(checkoutCustomerStrategy.isAnonymousCheckout())).willReturn(Boolean.TRUE);
		given(request.getHeader(AJAX_REQUEST_HEADER_NAME)).willReturn(AJAX_REQUEST_HEADER_NAME);
		final CartModel cartModel = mock(CartModel.class);
		guestCheckoutCartCleanStrategy.cleanGuestCart(request);

		verifyNoMoreInteractions(cartModel);
	}

	@Test
	public void testWhetherURLContainsCheckoutPattern() throws IOException, ServletException
	{
		given(request.getRequestURL()).willReturn(
				new StringBuffer("https://electronics.local:9002/customstorefront/electronics/en/my-account/addressform"));
		assertEquals(Boolean.FALSE, Boolean.valueOf(guestCheckoutCartCleanStrategy.checkWhetherURLContainsSkipPattern(request)));

		given(request.getRequestURL())
				.willReturn(new StringBuffer("https://electronics.local:9002/customstorefront/electronics/en/checkou/multi"));
		assertEquals(Boolean.FALSE, Boolean.valueOf(guestCheckoutCartCleanStrategy.checkWhetherURLContainsSkipPattern(request)));

		given(request.getRequestURL())
				.willReturn(new StringBuffer("electronics.local:9002/customstorefront/electronics/en/checkout/multi"));
		assertEquals(Boolean.FALSE, Boolean.valueOf(guestCheckoutCartCleanStrategy.checkWhetherURLContainsSkipPattern(request)));

		given(request.getRequestURL())
				.willReturn(new StringBuffer("https://electronics.local:9002/customstorefront/electronics/en/checkout/multi"));
		assertEquals(Boolean.TRUE, Boolean.valueOf(guestCheckoutCartCleanStrategy.checkWhetherURLContainsSkipPattern(request)));

		given(request.getRequestURL()).willReturn(new StringBuffer(
				"https://electronics.local:9002/customstorefront/electronics/en/checkout/multi/payment-method/add"));
		assertEquals(Boolean.TRUE, Boolean.valueOf(guestCheckoutCartCleanStrategy.checkWhetherURLContainsSkipPattern(request)));

		given(request.getRequestURL()).willReturn(new StringBuffer(
				"https://electronics.local:9002/customstorefront/electronics/en/checkout/multi/delivery-address/add"));
		assertEquals(Boolean.TRUE, Boolean.valueOf(guestCheckoutCartCleanStrategy.checkWhetherURLContainsSkipPattern(request)));
	}

	@Test
	public void testWhetherURLContainsFaviconPattern() throws IOException, ServletException
	{
		given(request.getRequestURL())
				.willReturn(new StringBuffer("https://electronics.local:9002/customstorefront/favicon"));
		assertEquals(Boolean.FALSE, Boolean.valueOf(guestCheckoutCartCleanStrategy.checkWhetherURLContainsSkipPattern(request)));

		given(request.getRequestURL()).willReturn(new StringBuffer(
				"https://electronics.local:9002/customstorefront/_ui/responsive/theme-alpha/images/favicon.ico"));
		assertEquals(Boolean.TRUE, Boolean.valueOf(guestCheckoutCartCleanStrategy.checkWhetherURLContainsSkipPattern(request)));

		given(request.getRequestURL())
				.willReturn(new StringBuffer("https://electronics.local:9002/customstorefront/favicon.ico"));
		assertEquals(Boolean.TRUE, Boolean.valueOf(guestCheckoutCartCleanStrategy.checkWhetherURLContainsSkipPattern(request)));

		given(request.getRequestURL())
				.willReturn(new StringBuffer("https://electronics.local:9002/_ui/responsive/theme-alpha/images/favicon.ico"));
		assertEquals(Boolean.TRUE, Boolean.valueOf(guestCheckoutCartCleanStrategy.checkWhetherURLContainsSkipPattern(request)));

		given(request.getRequestURL()).willReturn(new StringBuffer("https://electronics.local:9002/favicon.ico"));
		assertEquals(Boolean.TRUE, Boolean.valueOf(guestCheckoutCartCleanStrategy.checkWhetherURLContainsSkipPattern(request)));
	}

	// --- NET-8942: advance payment on an abandoned guest cart ---

	@Test
	public void shouldRemoveTheAdvancePaymentInfoOfAnAbandonedGuestCartAfterSavingTheCart()
	{
		final AddressModel billingAddress = new AddressModel();
		final AdvancePaymentInfoModel advancePaymentInfo = new AdvancePaymentInfoModel();
		advancePaymentInfo.setBillingAddress(billingAddress);
		final CartModel cart = guestCartWith(advancePaymentInfo);
		givenAGuestLeavingTheCheckout(cart);

		guestCheckoutCartCleanStrategy.cleanGuestCart(request);

		assertNull(cart.getPaymentInfo());
		assertNull("the payment mode belongs to the dropped advance payment", cart.getPaymentMode());

		// removed only after the cart no longer references it
		final InOrder inOrder = inOrder(cartService, modelService);
		inOrder.verify(cartService).saveOrder(cart);
		@SuppressWarnings("unchecked")
		final ArgumentCaptor<Collection<Object>> removed = ArgumentCaptor.forClass(Collection.class);
		inOrder.verify(modelService).removeAll(removed.capture());
		assertTrue(removed.getValue().stream().anyMatch(item -> item == advancePaymentInfo));
		assertTrue(removed.getValue().stream().anyMatch(item -> item == billingAddress));
	}

	@Test
	public void shouldNotRemoveTheCardOfAnAbandonedGuestCart()
	{
		final CreditCardPaymentInfoModel card = new CreditCardPaymentInfoModel();
		card.setBillingAddress(new AddressModel());
		final CartModel cart = guestCartWith(card);
		givenAGuestLeavingTheCheckout(cart);

		guestCheckoutCartCleanStrategy.cleanGuestCart(request);

		assertNull(cart.getPaymentInfo());
		verify(cartService).saveOrder(cart);
		verifyNoInteractions(modelService);
	}

	@Test
	public void shouldNotTouchTheAdvancePaymentOfAGuestStillInTheCheckout()
	{
		given(sessionService.getAttribute(WebConstants.ANONYMOUS_CHECKOUT)).willReturn(Boolean.TRUE);
		given(Boolean.valueOf(checkoutCustomerStrategy.isAnonymousCheckout())).willReturn(Boolean.TRUE);
		given(request.getMethod()).willReturn(HTTP_REQUEST_GET_METHOD);
		given(request.getRequestURL()).willReturn(
				new StringBuffer("https://electronics.local:9002/customstorefront/electronics/en/checkout/multi/summary/view"));

		guestCheckoutCartCleanStrategy.cleanGuestCart(request);

		verify(cartService, never()).saveOrder(any(CartModel.class));
		verifyNoInteractions(modelService);
	}

	private static CartModel guestCartWith(final PaymentInfoModel paymentInfo)
	{
		final PaymentModeModel advanceMode = new PaymentModeModel();
		advanceMode.setCode("advance");
		final CartModel cart = new CartModel();
		cart.setDeliveryAddress(new AddressModel());
		cart.setPaymentInfo(paymentInfo);
		cart.setPaymentMode(paymentInfo instanceof AdvancePaymentInfoModel ? advanceMode : null);
		return cart;
	}

	private void givenAGuestLeavingTheCheckout(final CartModel cart)
	{
		given(sessionService.getAttribute(WebConstants.ANONYMOUS_CHECKOUT)).willReturn(Boolean.TRUE);
		given(Boolean.valueOf(checkoutCustomerStrategy.isAnonymousCheckout())).willReturn(Boolean.TRUE);
		given(request.getMethod()).willReturn(HTTP_REQUEST_GET_METHOD);
		given(request.getRequestURL()).willReturn(new StringBuffer(
				"https://electronics.local:9002/customstorefront/electronics/en/Open-Catalogue/Cameras/p/1934793"));
		given(cartService.getSessionCart()).willReturn(cart);
	}
}
