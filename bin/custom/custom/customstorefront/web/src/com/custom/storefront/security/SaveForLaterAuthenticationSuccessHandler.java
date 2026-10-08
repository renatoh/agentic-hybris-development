/*
 * Copyright (c) 2026 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.custom.storefront.security;

import de.hybris.platform.acceleratorstorefrontcommons.security.StorefrontAuthenticationSuccessHandler;
import de.hybris.platform.servicelayer.session.SessionService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Required;

import com.custom.storefront.controllers.pages.CartPageController;


/**
 * Forces the post-login redirect to {@code /cart} when an anonymous shopper's "save for later"
 * click is pending (NET-8941 section 5.4, acceptance criterion 1a).
 * <p>
 * {@code WebConstants.CONTINUE_URL} was assumed by the spec to already drive this - verified it
 * does not: it is only ever read by {@code AbstractCartPageController}/{@code CheckoutController}
 * to render the "continue shopping" link, never consulted by
 * {@code StorefrontAuthenticationSuccessHandler#determineTargetUrl} for the actual redirect target.
 * With no saved request either (the anonymous shopper is redirected to {@code /login} directly by
 * {@code CartPageController}, not challenged by the security filter, so there is nothing for
 * {@code SavedRequestAwareAuthenticationSuccessHandler} to replay), the platform bean falls through
 * to its configured {@code defaultTargetUrl} - the homepage, not {@code /cart}.
 * <p>
 * This overrides the same protected extension point the platform bean already uses for its own
 * checkout-after-cart-merge special case (see {@code determineTargetUrl}'s {@code CHECKOUT_URL} ->
 * {@code CART_URL} logic) - not a new {@code AuthenticationSuccessHandler} built from scratch, and
 * unrelated to either alternative the spec explicitly ruled out ({@code @RequireHardLogIn}'s
 * before-controller redirect, and {@code RequestCache}/saved-request replay).
 * <p>
 * Reads the pending flag via {@link SessionService}, not the raw servlet {@code HttpSession} -
 * {@code CartPageController} sets/reads it through {@code SessionService} too, which is backed by
 * the JaloSession's own attribute map, a distinct store from {@code HttpSession} on this platform.
 * Follows the same pattern as the platform's own {@code StorefrontLogoutSuccessHandler} (same
 * package), which likewise injects {@code SessionService} rather than touching {@code HttpSession}
 * directly for session-service-scoped state.
 */
public class SaveForLaterAuthenticationSuccessHandler extends StorefrontAuthenticationSuccessHandler
{
	private static final String CART_URL = "/cart";

	private SessionService sessionService;

	@Override
	protected String determineTargetUrl(final HttpServletRequest request, final HttpServletResponse response)
	{
		final String targetUrl = super.determineTargetUrl(request, response);
		if (getSessionService().getAttribute(CartPageController.PENDING_SAVE_FOR_LATER_PRODUCT_CODE) != null)
		{
			return CART_URL;
		}
		return targetUrl;
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
}
