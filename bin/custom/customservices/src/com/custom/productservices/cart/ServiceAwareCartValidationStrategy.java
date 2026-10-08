package com.custom.productservices.cart;

import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationStatus;
import de.hybris.platform.commerceservices.strategies.impl.DefaultCartValidationStrategy;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;

import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943: the platform validation (run on {@code /cart/checkout}) looks every entry's product up by code and
 * <em>removes</em> the entry when that fails. Service products are hidden from storefront lookups
 * ({@code Frontend_ServiceProduct}), so their entries would be removed at checkout. Services are not physical, so there is
 * no stock to check either. Whether a service is still valid is checked by the calculation hook and the place-order guard.
 * Upgrade check: compare with {@code DefaultCartValidationStrategy.validateCartEntry}.
 */
public class ServiceAwareCartValidationStrategy extends DefaultCartValidationStrategy
{
	@Override
	protected CommerceCartModification validateCartEntry(final CartModel cartModel, final CartEntryModel cartEntryModel)
	{
		if (cartEntryModel.getProduct() instanceof ServiceProductModel)
		{
			final CommerceCartModification modification = new CommerceCartModification();
			modification.setStatusCode(CommerceCartModificationStatus.SUCCESS);
			modification.setQuantityAdded(cartEntryModel.getQuantity().longValue());
			modification.setQuantity(cartEntryModel.getQuantity().longValue());
			modification.setEntry(cartEntryModel);
			return modification;
		}
		return super.validateCartEntry(cartModel, cartEntryModel);
	}
}
