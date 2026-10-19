package com.custom.productservices.cart;

import de.hybris.platform.commerceservices.order.hook.CommerceCartCalculationMethodHook;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.servicelayer.session.SessionService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.custom.constants.CustomservicesConstants;
import com.custom.core.model.ServiceProductModel;

import com.custom.productservices.service.ServiceEntryGroupService;


/**
 * NET-8943 &sect;5.4 quantity sync and stale-service cleanup, last line of defence: merging a plain add into an existing product line goes through
 * {@code CartService.updateQuantities}, which calls no update hook. Every one of those paths calculates the cart
 * afterwards, so service quantities are aligned with their product line right before each calculation.
 */
public class ServiceQuantitySyncCalculationHook implements CommerceCartCalculationMethodHook
{
	private ServiceEntryGroupService serviceEntryGroupService;
	private CartServiceSelectionService cartServiceSelectionService;
	private SessionService sessionService;

	@Override
	public void beforeCalculate(final CommerceCartParameter parameter)
	{
		final CartModel cart = parameter == null ? null : parameter.getCart();
		if (cart == null || cart.getEntries() == null)
		{
			return;
		}
		// an invalid service entry cannot be priced and would fail the calculation (restore, update, merge, checkout)
		rememberRemoved(cartServiceSelectionService.removeInvalidServicesBeforeCalculation(cart));
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			if (!serviceEntryGroupService.isServiceEntry(entry))
			{
				cartServiceSelectionService.syncServiceQuantities(entry);
			}
		}
	}

	/** The shopper must be told: the cart page shows these names once and clears them. */
	protected void rememberRemoved(final List<ServiceProductModel> removed)
	{
		if (removed == null || removed.isEmpty())
		{
			return;
		}
		final List<String> names = new ArrayList<>();
		final List<String> pending = sessionService.getAttribute(CustomservicesConstants.REMOVED_SERVICES_SESSION_ATTRIBUTE);
		if (pending != null)
		{
			names.addAll(pending);
		}
		names.addAll(removed.stream().map(ServiceProductModel::getName).collect(Collectors.toList()));
		sessionService.setAttribute(CustomservicesConstants.REMOVED_SERVICES_SESSION_ATTRIBUTE, names);
	}

	@Override
	public void afterCalculate(final CommerceCartParameter parameter)
	{
		// nothing to do
	}

	public void setServiceEntryGroupService(final ServiceEntryGroupService serviceEntryGroupService)
	{
		this.serviceEntryGroupService = serviceEntryGroupService;
	}

	public void setSessionService(final SessionService sessionService)
	{
		this.sessionService = sessionService;
	}

	public void setCartServiceSelectionService(final CartServiceSelectionService cartServiceSelectionService)
	{
		this.cartServiceSelectionService = cartServiceSelectionService;
	}
}
