/*
 * Copyright (c) 2019 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.storefront.security.impl;

import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.payment.AdvancePaymentInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.servicelayer.user.UserService;
import com.custom.storefront.security.GuestCheckoutCartCleanStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;


public class DefaultGuestCheckoutCartCleanStrategy implements GuestCheckoutCartCleanStrategy
{
	public static final String AJAX_REQUEST_HEADER_NAME = "X-Requested-With";

	private List<String> skipPatterns;
	private CheckoutCustomerStrategy checkoutCustomerStrategy;
	private CartService cartService;
	private SessionService sessionService;
	private UserService userService;
	private ModelService modelService;

	@Override
	public void cleanGuestCart(final HttpServletRequest request)
	{

		if (isAnonymousCheckout() && StringUtils.isBlank(request.getHeader(AJAX_REQUEST_HEADER_NAME)) && isGetMethod(request)
				&& !checkWhetherURLContainsSkipPattern(request))
		{
			final CartModel cartModel = getCartService().getSessionCart();
			final PaymentInfoModel paymentInfo = cartModel.getPaymentInfo();
			cartModel.setDeliveryAddress(null);
			cartModel.setDeliveryMode(null);
			cartModel.setPaymentInfo(null);
			// NET-8942: the payment mode belongs to the payment info that is dropped here (advance payment)
			cartModel.setPaymentMode(null);
			cartModel.setUser(getUserService().getAnonymousUser());
			getCartService().saveOrder(cartModel);
			if (paymentInfo instanceof AdvancePaymentInfoModel)
			{
				removeAdvancePaymentInfo((AdvancePaymentInfoModel) paymentInfo);
			}
			getSessionService().removeAttribute(WebConstants.ANONYMOUS_CHECKOUT);
			getSessionService().removeAttribute(WebConstants.ANONYMOUS_CHECKOUT_GUID);
		}

	}

	/**
	 * NET-8942: an AdvancePaymentInfo is owned by the cart and referenced by nothing else once dropped from it - remove it
	 * with its billing address instead of leaving it behind.
	 */
	protected void removeAdvancePaymentInfo(final AdvancePaymentInfoModel paymentInfo)
	{
		final List<Object> toRemove = new ArrayList<>(2);
		if (paymentInfo.getBillingAddress() != null)
		{
			toRemove.add(paymentInfo.getBillingAddress());
		}
		toRemove.add(paymentInfo);
		getModelService().removeAll(toRemove);
	}

	@Override
	public boolean checkWhetherURLContainsSkipPattern(final HttpServletRequest request)
	{
		boolean contains = false;
		final String requestUrl = request.getRequestURL().toString();
		for (final String patternStr : skipPatterns)
		{
			final Pattern pattern = Pattern.compile(patternStr);
			if (pattern.matcher(requestUrl).matches())
			{
				contains = true;
				break;
			}
		}
		return contains;
	}

	protected boolean isGetMethod(final HttpServletRequest httpRequest)
	{
		return "GET".equalsIgnoreCase(httpRequest.getMethod());
	}

	protected CheckoutCustomerStrategy getCheckoutCustomerStrategy()
	{
		return checkoutCustomerStrategy;
	}

	protected boolean isAnonymousCheckout()
	{
		return Boolean.TRUE.equals(getSessionService().getAttribute(WebConstants.ANONYMOUS_CHECKOUT))
				&& getCheckoutCustomerStrategy().isAnonymousCheckout();
	}

	@Required
	public void setCheckoutCustomerStrategy(final CheckoutCustomerStrategy checkoutCustomerStrategy)
	{
		this.checkoutCustomerStrategy = checkoutCustomerStrategy;
	}

	protected CartService getCartService()
	{
		return cartService;
	}

	@Required
	public void setCartService(final CartService cartService)
	{
		this.cartService = cartService;
	}

	protected SessionService getSessionService()
	{
		return sessionService;
	}

	@Required
	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	protected UserService getUserService()
	{
		return userService;
	}

	@Required
	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	protected ModelService getModelService()
	{
		return modelService;
	}

	@Required
	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}

	public List<String> getSkipPatterns()
	{
		return skipPatterns;
	}

	@Required
	public void setSkipPatterns(final List<String> skipPatterns)
	{
		this.skipPatterns = skipPatterns;
	}

}
