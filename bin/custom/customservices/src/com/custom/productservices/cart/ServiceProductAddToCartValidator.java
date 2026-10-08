package com.custom.productservices.cart;

import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.order.validator.AddToCartValidator;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;

import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943 &sect;4.2: service products are never added through the normal add-to-cart path (controllers, quick add,
 * REST). Services are attached only through {@code CartServiceSelectionService}.
 */
public class ServiceProductAddToCartValidator implements AddToCartValidator
{
	@Override
	public boolean supports(final CommerceCartParameter parameter)
	{
		return parameter != null && parameter.getProduct() instanceof ServiceProductModel;
	}

	@Override
	public void validate(final CommerceCartParameter parameter) throws CommerceCartModificationException
	{
		throw new CommerceCartModificationException("Services cannot be added to the cart on their own");
	}
}
