package com.custom.productservices.cart;

import de.hybris.platform.commerceservices.order.hook.CommercePlaceOrderMethodHook;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.service.data.CommerceOrderResult;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.InvalidCartException;


/**
 * NET-8943 &sect;5.4: nothing is charged for a service that is no longer offered. If the cart holds a service entry whose
 * reference, price condition or price row has gone since it was added, placing the order is refused instead of silently
 * dropping a line the shopper has not been told about. The cart page then removes it and says so.
 */
public class ServiceValidationPlaceOrderMethodHook implements CommercePlaceOrderMethodHook
{
	private CartServiceSelectionService cartServiceSelectionService;

	@Override
	public void beforePlaceOrder(final CommerceCheckoutParameter parameter) throws InvalidCartException
	{
		final CartModel cart = parameter == null ? null : parameter.getCart();
		if (cart != null && cartServiceSelectionService.hasInvalidServices(cart))
		{
			throw new InvalidCartException("The cart contains a service that is no longer available: cart " + cart.getCode());
		}
	}

	@Override
	public void afterPlaceOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult orderModel)
	{
		// nothing to do
	}

	@Override
	public void beforeSubmitOrder(final CommerceCheckoutParameter parameter, final CommerceOrderResult result)
	{
		// nothing to do
	}

	public void setCartServiceSelectionService(final CartServiceSelectionService cartServiceSelectionService)
	{
		this.cartServiceSelectionService = cartServiceSelectionService;
	}
}
