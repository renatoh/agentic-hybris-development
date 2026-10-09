package com.custom.productservices.cart;

import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.commerceservices.order.hook.CommerceAddToCartMethodHook;
import de.hybris.platform.commerceservices.order.hook.CommerceUpdateCartEntryHook;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.servicelayer.model.ModelService;

import com.custom.productservices.service.ServiceEntryGroupService;


/**
 * NET-8943 &sect;5.4 quantity sync: hooked into the commerce update-cart-entry path, so every caller is covered (cart
 * page, cart merge on login, restoration), not only the controller. After a product entry's quantity changed, its service
 * entries get the same quantity.
 */
public class ServiceQuantitySyncUpdateCartEntryHook implements CommerceUpdateCartEntryHook, CommerceAddToCartMethodHook
{
	private CartServiceSelectionService cartServiceSelectionService;
	private ServiceEntryGroupService serviceEntryGroupService;
	private CommerceCartService commerceCartService;
	private ModelService modelService;

	@Override
	public void beforeUpdateCartEntry(final CommerceCartParameter parameter)
	{
		// nothing to do: removal of a product entry is cascaded by ServiceEntryRemoveInterceptor
	}

	@Override
	public void afterUpdateCartEntry(final CommerceCartParameter parameter, final CommerceCartModification result)
	{
		syncAfterChange(parameter == null ? null : parameter.getCart(), result);
	}

	/** Adding a product that is already in the cart merges into its line without going through the update hooks. */
	@Override
	public void beforeAddToCart(final CommerceCartParameter parameters)
	{
		// nothing to do
	}

	@Override
	public void afterAddToCart(final CommerceCartParameter parameters, final CommerceCartModification result)
	{
		syncAfterChange(parameters == null ? null : parameters.getCart(), result);
	}

	protected void syncAfterChange(final CartModel cart, final CommerceCartModification result)
	{
		if (cart == null)
		{
			return;
		}
		final AbstractOrderEntryModel entry = result == null ? null : result.getEntry();
		if (entry == null || modelService.isNew(entry) || entry.getOrder() == null)
		{
			// the entry was removed (quantity 0): ServiceEntryRemoveInterceptor removed its services, drop the empty group
			modelService.refresh(cart);
			cartServiceSelectionService.removeEmptyServiceGroups(cart);
			return;
		}
		if (serviceEntryGroupService.isServiceEntry(entry))
		{
			return;
		}
		if (cartServiceSelectionService.syncServiceQuantities(entry))
		{
			modelService.refresh(cart);
			final CommerceCartParameter calc = new CommerceCartParameter();
			calc.setEnableHooks(true);
			calc.setCart(cart);
			commerceCartService.calculateCart(calc);
		}
	}

	public void setCartServiceSelectionService(final CartServiceSelectionService cartServiceSelectionService)
	{
		this.cartServiceSelectionService = cartServiceSelectionService;
	}

	public void setServiceEntryGroupService(final ServiceEntryGroupService serviceEntryGroupService)
	{
		this.serviceEntryGroupService = serviceEntryGroupService;
	}

	public void setCommerceCartService(final CommerceCartService commerceCartService)
	{
		this.commerceCartService = commerceCartService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}
}
