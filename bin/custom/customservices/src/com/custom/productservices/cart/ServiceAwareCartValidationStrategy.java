package com.custom.productservices.cart;

import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.strategies.impl.DefaultCartValidationStrategy;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;


/**
 * NET-8943: cart validation (run on {@code /cart/checkout}) that keeps service entries; see
 * {@link ServiceCartEntryValidation}. Upgrade check: compare with {@code DefaultCartValidationStrategy.validateCartEntry}.
 */
public class ServiceAwareCartValidationStrategy extends DefaultCartValidationStrategy
{
	@Override
	protected CommerceCartModification validateCartEntry(final CartModel cartModel, final CartEntryModel cartEntryModel)
	{
		if (ServiceCartEntryValidation.isServiceEntry(cartEntryModel))
		{
			return ServiceCartEntryValidation.validServiceEntry(cartEntryModel);
		}
		return super.validateCartEntry(cartModel, cartEntryModel);
	}
}
