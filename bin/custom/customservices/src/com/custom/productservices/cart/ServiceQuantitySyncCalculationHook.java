package com.custom.productservices.cart;

import de.hybris.platform.commerceservices.order.hook.CommerceCartCalculationMethodHook;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;

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

	@Override
	public void beforeCalculate(final CommerceCartParameter parameter)
	{
		final CartModel cart = parameter == null ? null : parameter.getCart();
		if (cart == null || cart.getEntries() == null)
		{
			return;
		}
		// an invalid service entry cannot be priced and would fail the calculation (restore, update, merge, checkout)
		cartServiceSelectionService.removeInvalidServicesBeforeCalculation(cart);
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			if (!serviceEntryGroupService.isServiceEntry(entry))
			{
				cartServiceSelectionService.syncServiceQuantities(entry);
			}
		}
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

	public void setCartServiceSelectionService(final CartServiceSelectionService cartServiceSelectionService)
	{
		this.cartServiceSelectionService = cartServiceSelectionService;
	}
}
