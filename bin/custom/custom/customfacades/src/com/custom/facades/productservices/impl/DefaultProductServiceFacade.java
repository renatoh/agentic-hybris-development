package com.custom.facades.productservices.impl;

import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.CartService;
import de.hybris.platform.servicelayer.session.SessionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.custom.constants.CustomservicesConstants;
import com.custom.core.model.ServiceProductModel;
import com.custom.facades.productservices.ProductServiceFacade;
import com.custom.productservices.cart.CartServiceSelectionService;
import com.custom.productservices.exceptions.CartServiceSelectionException;
import com.custom.productservices.service.ServiceEntryGroupService;


public class DefaultProductServiceFacade implements ProductServiceFacade
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultProductServiceFacade.class);

	private CartService cartService;
	private SessionService sessionService;
	private CartServiceSelectionService cartServiceSelectionService;
	private ServiceEntryGroupService serviceEntryGroupService;

	@Override
	public void addServiceToCart(final int productEntryNumber, final String serviceCode) throws CartServiceSelectionException
	{
		cartServiceSelectionService.addService(cartService.getSessionCart(), productEntryNumber, serviceCode);
	}

	@Override
	public void removeServiceFromCart(final int productEntryNumber, final String serviceCode) throws CartServiceSelectionException
	{
		cartServiceSelectionService.removeService(cartService.getSessionCart(), productEntryNumber, serviceCode);
	}

	@Override
	public List<String> removeInvalidServicesFromCart()
	{
		// names of services that a calculation removed earlier in this session (restore, update, add, checkout)
		final List<String> names = new ArrayList<>();
		final List<String> pending = sessionService.getAttribute(CustomservicesConstants.REMOVED_SERVICES_SESSION_ATTRIBUTE);
		if (pending != null)
		{
			names.addAll(pending);
			sessionService.removeAttribute(CustomservicesConstants.REMOVED_SERVICES_SESSION_ATTRIBUTE);
		}
		if (!cartService.hasSessionCart())
		{
			return names;
		}
		try
		{
			names.addAll(cartServiceSelectionService.removeInvalidServices(cartService.getSessionCart()).stream()
					.map(ServiceProductModel::getName).collect(Collectors.toList()));
		}
		catch (final RuntimeException e)
		{
			LOG.error("Could not clean up invalid services, loading the cart anyway", e);
		}
		return names;
	}

	@Override
	public boolean isServiceEntry(final int entryNumber)
	{
		if (!cartService.hasSessionCart())
		{
			return false;
		}
		final CartModel cart = cartService.getSessionCart();
		return cart.getEntries() != null && cart.getEntries().stream()
				.anyMatch(e -> e.getEntryNumber() != null && e.getEntryNumber().intValue() == entryNumber
						&& serviceEntryGroupService.isServiceEntry(e));
	}

	public void setCartService(final CartService cartService)
	{
		this.cartService = cartService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setCartServiceSelectionService(final CartServiceSelectionService cartServiceSelectionService)
	{
		this.cartServiceSelectionService = cartServiceSelectionService;
	}

	public void setServiceEntryGroupService(final ServiceEntryGroupService serviceEntryGroupService)
	{
		this.serviceEntryGroupService = serviceEntryGroupService;
	}
}
