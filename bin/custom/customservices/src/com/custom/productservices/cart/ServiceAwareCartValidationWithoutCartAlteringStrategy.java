package com.custom.productservices.cart;

import de.hybris.platform.commerceservices.order.CommerceCartModification;
import de.hybris.platform.commerceservices.order.CommerceCartModificationStatus;
import de.hybris.platform.commerceservices.strategies.impl.CartValidationWithoutCartAlteringStrategy;
import de.hybris.platform.core.model.order.CartEntryModel;

import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943: read-only twin of {@link ServiceAwareCartValidationStrategy}. Without this the entry would be reported as
 * unavailable because the service product cannot be looked up by code in the storefront.
 */
public class ServiceAwareCartValidationWithoutCartAlteringStrategy extends CartValidationWithoutCartAlteringStrategy
{
	@Override
	protected CommerceCartModification validateCartEntry(final CartEntryModel cartEntryModel)
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
		return super.validateCartEntry(cartEntryModel);
	}
}
